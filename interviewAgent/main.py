import asyncio
import os
import json
import hashlib
import logging
import aiohttp
from datetime import datetime, timezone
from dotenv import load_dotenv
from PIL import Image

from livekit.agents import AutoSubscribe, JobContext, WorkerOptions, cli
from livekit.agents import AgentSession, Agent, llm
from livekit.plugins import google
from livekit import rtc
from google import genai
from google.genai import types

load_dotenv()
logger = logging.getLogger("owlyn-worker")

# Initialize the standard Google GenAI client for the Sentinels (Agent 3 - Vision)
genai_client = genai.Client(api_key=os.getenv("GOOGLE_API_KEY"))


async def entrypoint(ctx: JobContext):
    logger.info(f"Connecting to room: {ctx.room.name}")
    await ctx.connect(auto_subscribe=AutoSubscribe.SUBSCRIBE_ALL)

    interview_id = ctx.room.name.replace("interview-", "")

    # --- FETCH CONFIG FROM JAVA ---
    logger.info(f"Fetching config for interview: {interview_id}")
    java_url = f"{os.getenv('JAVA_BACKEND_URL')}/api/internal/reports/interviews/{interview_id}/config"
    config_headers = {"X-Internal-Token": os.getenv("INTERNAL_PYTHON_SECRET")}

    async with aiohttp.ClientSession() as config_session:
        async with config_session.get(java_url, headers=config_headers) as config_resp:
            if config_resp.status == 200:
                config_data = await config_resp.json()
                session_mode = config_data.get("mode", "STANDARD")
                agent_instructions = (config_data.get("systemPrompt") or "").strip()
                report_access_code = config_data.get("accessCode", "UNKNOWN")
                prompt_source = "backend"
                logger.info(
                    f"Interview config loaded: status={config_resp.status}, mode={session_mode}, accessCode={report_access_code}"
                )

                if not agent_instructions:
                    logger.error("Interview config missing systemPrompt; refusing to start with fallback prompt.")
                    return
            else:
                body_preview = await config_resp.text()
                logger.error(
                    f"Interview config fetch failed: status={config_resp.status}, refusing to start without backend prompt, body={body_preview[:300]}"
                )
                return

    prompt_hash = hashlib.sha256(agent_instructions.encode("utf-8")).hexdigest()[:12]
    logger.info(
        f"Prompt source={prompt_source}, length={len(agent_instructions)}, sha256={prompt_hash}, preview={agent_instructions[:180].replace(chr(10), ' ')}"
    )
    # -----------------------------------

    candidate_code = report_access_code
    transcript_builder: list[str] = []
    latest_cam_frame: rtc.VideoFrame | None = None
    latest_screen_frame: rtc.VideoFrame | None = None
    shutdown_event = asyncio.Event()
    finalize_lock = asyncio.Lock()
    has_finalized = False
    forced_end_dispatched = False
    capture_tasks: list[asyncio.Task] = []

    # ==========================================
    # 1. AGENT 2: NATIVE GEMINI LIVE API (BIDI)
    # ==========================================

    logger.info("Initializing True Native Gemini Live Model...")

    # This is the exact plugin from the LiveKit documentation!
    # It bypasses STT/TTS and streams native audio directly to Google.
    realtime_model_kwargs = {
        "model": "gemini-2.5-flash-native-audio-preview-12-2025",
        "voice": "Puck",
        "temperature": 0.7,
        "context_window_compression": types.ContextWindowCompressionConfig(
            sliding_window=types.SlidingWindow(),
        ),
        "session_resumption": types.SessionResumptionConfig(),
    }

    if session_mode == "TUTOR":
        realtime_model_kwargs["tools"] = [{"google_search": {}}]

    realtime_model = google.realtime.RealtimeModel(**realtime_model_kwargs)

    # AgentSession is built specifically for RealtimeModels, NOT pipelines!
    agent_session = AgentSession(
        llm=realtime_model
    )

    async def dispatch_end_interview(reason: str, message: str):
        nonlocal forced_end_dispatched
        if forced_end_dispatched:
            return

        forced_end_dispatched = True
        payload = json.dumps(
            {
                "type": "END_INTERVIEW",
                "reason": reason,
                "message": message,
            }
        ).encode("utf-8")

        try:
            await ctx.room.local_participant.publish_data(payload, reliable=True)
            logger.warning(f"END_INTERVIEW signal sent to frontend. reason={reason}")
        except Exception as e:
            logger.warning(f"Failed sending END_INTERVIEW signal: {e}")

    @llm.function_tool(
        name="end_interview_session",
        description=(
            "Force-ends the current interview session. "
            "Call this when the candidate is non-serious, violates interview policy, "
            "or the interview must be terminated immediately."
        ),
    )
    async def end_interview_session(
        reason: str = "AGENT_TERMINATION",
        message: str = "The interviewer terminated this interview due to non-serious behavior.",
    ) -> str:
        await dispatch_end_interview(reason, message)
        transcript_builder.append(f"[SYSTEM TERMINATION]: reason={reason}; message={message}")
        asyncio.create_task(finalize_interview("agent_tool_termination"))
        logger.warning(f"Tool called: end_interview_session(reason={reason})")
        return "Interview end signal dispatched."

    await agent_session.start(
        room=ctx.room,
        agent=Agent(instructions=agent_instructions, tools=[end_interview_session]),
    )

    logger.info("Gemini Live session started with configured system prompt and function tools.")

    try:
        if session_mode == "TUTOR":
            await agent_session.generate_reply(
                instructions="Greet briefly as Owlyn Assistant, confirm you can see the shared screen, and ask what coding/email/browsing task the user wants help with first."
            )
        else:
            await agent_session.generate_reply(
                instructions="Start the interview now. Give a brief greeting, confirm you are the interviewer, and ask the first interview question immediately."
            )
    except Exception as e:
        logger.warning(f"Initial generate_reply failed; continuing with normal turn handling. error={e}")

    def append_transcript_line(role_label: str, text: str):
        clean_text = (text or "").strip()
        if not clean_text:
            return
        transcript_builder.append(f"[{role_label}]: {clean_text}")
        logger.info(f"TRANSCRIPT [{role_label}]: {clean_text}")

        speaker = "candidate" if role_label == "CANDIDATE" else "ai"
        asyncio.create_task(publish_transcript_event(speaker, clean_text))

    async def publish_transcript_event(speaker: str, text: str):
        payload = {
            "type": "transcript",
            "speaker": speaker,
            "text": text,
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "id": f"{speaker}-{int(asyncio.get_running_loop().time() * 1000)}",
        }
        try:
            await ctx.room.local_participant.publish_data(
                json.dumps(payload).encode("utf-8"),
                reliable=True,
            )
        except Exception as e:
            logger.warning(f"Failed publishing transcript event to LiveKit: {e}")

    @agent_session.on("function_tools_executed")
    def on_function_tools_executed(event):
        for fnc_call, fnc_output in event.zipped():
            output_text = getattr(fnc_output, "output", "") if fnc_output else ""
            is_error = bool(getattr(fnc_output, "is_error", False)) if fnc_output else False
            logger.warning(
                "FUNCTION_TOOL_EXECUTED "
                f"name={fnc_call.name} "
                f"args={fnc_call.arguments} "
                f"error={is_error} "
                f"output={output_text}"
            )

    # Primary transcript event for LiveKit 1.4.x
    @agent_session.on("conversation_item_added")
    def on_conversation_item_added(event):
        item = getattr(event, "item", None)
        if not isinstance(item, llm.ChatMessage):
            return

        role = (item.role or "").lower()
        text = item.text_content or ""
        if role == "assistant":
            append_transcript_line("OWLYN", text)
        elif role == "user":
            append_transcript_line("CANDIDATE", text)

    # Compatibility fallback in case specific speech events are emitted
    @agent_session.on("agent_speech_committed")
    def on_agent_speech(msg):
        append_transcript_line("OWLYN", getattr(msg, "text", ""))

    @agent_session.on("user_speech_committed")
    def on_user_speech(msg):
        append_transcript_line("CANDIDATE", getattr(msg, "text", ""))

    async def notify_live_agent(trigger: str, instructions: str):
        if shutdown_event.is_set():
            logger.info(f"Skipping live-agent notify for {trigger}; session is shutting down.")
            return False

        logger.info(f"Dispatching live-agent notify for {trigger}.")
        try:
            await agent_session.generate_reply(instructions=instructions)
            logger.info(f"Live-agent notify delivered for {trigger}.")
            return True
        except Exception as e:
            logger.warning(f"Live-agent notify failed for {trigger}: {e}")
            return False

    # ==========================================
    # 2. SEPARATED VIDEO CAPTURE
    # ==========================================
    @ctx.room.on("track_subscribed")
    def on_track_subscribed(track: rtc.Track, publication: rtc.TrackPublication, participant: rtc.RemoteParticipant):
        nonlocal candidate_code
        if candidate_code == "UNKNOWN":
            candidate_code = participant.identity

        if track.kind == rtc.TrackKind.KIND_VIDEO:
            if publication.source == rtc.TrackSource.SOURCE_CAMERA:
                logger.info("Camera track received! Starting Proctor capture.")
                capture_tasks.append(asyncio.create_task(capture_cam_frames(rtc.VideoStream(track))))
            elif publication.source == rtc.TrackSource.SOURCE_SCREENSHARE:
                logger.info("Screen track received! Starting Workspace capture.")
                capture_tasks.append(asyncio.create_task(capture_screen_frames(rtc.VideoStream(track))))

    async def capture_cam_frames(stream: rtc.VideoStream):
        nonlocal latest_cam_frame
        async for event in stream:
            latest_cam_frame = event.frame

    async def capture_screen_frames(stream: rtc.VideoStream):
        nonlocal latest_screen_frame
        async for event in stream:
            latest_screen_frame = event.frame

    # ==========================================
    # 3. AGENT 3: THE SENTINELS (BACKGROUND LOOPS)
    # ==========================================
    async def analyze_frame_with_gemini(prompt: str, target_frame: rtc.VideoFrame | None) -> str:
        if target_frame is None:
            return "OK"

        try:
            rgba_buffer = target_frame.convert(rtc.VideoBufferType.RGBA)
            image = Image.frombytes("RGBA", (rgba_buffer.width, rgba_buffer.height), bytes(rgba_buffer.data))

            import io
            img_byte_arr = io.BytesIO()
            image.convert("RGB").save(img_byte_arr, format='JPEG', quality=70)
            img_bytes = img_byte_arr.getvalue()

            # Sentinels still use standard Vision for frame analysis
            response = await genai_client.aio.models.generate_content(
                model="gemini-3.1-flash-lite-preview",
                contents=[
                    types.Part.from_bytes(data=img_bytes, mime_type="image/jpeg"),
                    types.Part.from_text(text=prompt)
                ]
            )
            return response.text.strip() if response.text else "OK"
        except Exception as e:
            logger.error(f"Vision Analysis Failed: {e}")
            return "OK"

    async def proctor_sentinel():
        prompt = (
            "Analyze this webcam frame for interview-integrity activity. "
            "Detect and report concise warnings for activities such as: looking away repeatedly, using/holding a phone, eating/drinking, smoking/vaping, "
            "covering face, talking to another person, leaving frame, multiple people, suspicious gestures/devices, or any non-serious behavior. "
            "If no issue is visible, reply exactly 'OK'. "
            "If issue exists, reply with one short warning sentence only."
        )
        while not shutdown_event.is_set():
            await asyncio.sleep(10)
            if shutdown_event.is_set():
                break
            result = await analyze_frame_with_gemini(prompt, latest_cam_frame)

            if result != "OK" and result != "":
                logger.warning(f"Proctor Alert: {result}")
                transcript_builder.append(f"[PROCTOR ALERT]: {result}")

                # Inject alert into the Gemini Live API via generate_reply
                await notify_live_agent(
                    "PROCTOR_ALERT",
                    f"PROCTOR ALERT: {result}. Stop what you are doing and verbally warn the candidate right now."
                )

                payload = json.dumps({"type": "PROCTOR_WARNING", "message": result}).encode("utf-8")
                await ctx.room.local_participant.publish_data(payload, reliable=True)

                activity_payload = json.dumps(
                    {"type": "PROCTOR_ACTIVITY", "message": result}
                ).encode("utf-8")
                await ctx.room.local_participant.publish_data(activity_payload, reliable=True)

    async def workspace_sentinel():
        if session_mode == "TUTOR":
            prompt = "Look at this desktop screen. If there is a coding issue, email-writing issue, browsing/research opportunity, or workflow task you can help with, reply with one concise actionable assistant tip. If nothing actionable is visible, reply 'OK'."
        else:
            prompt = "Look at this code editor. Is there a syntax error or logic bug? If yes, reply with a short hint. If no, reply 'OK'."
        while not shutdown_event.is_set():
            await asyncio.sleep(15)
            if shutdown_event.is_set():
                break
            result = await analyze_frame_with_gemini(prompt, latest_screen_frame)

            if result != "OK" and result != "":
                logger.info(f"Workspace Alert: {result}")
                transcript_builder.append(f"[WORKSPACE BUG]: {result}")

                workspace_payload = json.dumps(
                    {"type": "WORKSPACE_ALERT", "message": result}
                ).encode("utf-8")
                await ctx.room.local_participant.publish_data(workspace_payload, reliable=True)

                # Inject alert into the Gemini Live API via generate_reply
                if session_mode == "TUTOR":
                    await notify_live_agent(
                        "WORKSPACE_ALERT",
                        f"ASSISTANT SCREEN INSIGHT: {result}. Respond as a practical desktop assistant and suggest the next concrete action."
                    )
                else:
                    await notify_live_agent(
                        "WORKSPACE_ALERT",
                        f"WORKSPACE ALERT: {result}. Give a concise interview-style hint and stay in interviewer role."
                    )

    proctor_task = None
    if session_mode != "TUTOR":
        logger.info("Starting Proctor Sentinel (Strict Mode)")
        proctor_task = asyncio.create_task(proctor_sentinel())
    else:
        logger.info("Tutor Mode: Proctor Sentinel DISABLED")

    workspace_task = asyncio.create_task(workspace_sentinel())

    # ==========================================
    # 4. INSTANT WAKE-UP (DATA CHANNELS)
    # ==========================================
    @ctx.room.on("data_received")
    def on_data_received(data_packet: rtc.DataPacket):
        try:
            payload = json.loads(data_packet.data.decode("utf-8"))
            if payload.get("event") == "RUN_CODE":
                logger.info("Candidate clicked RUN! Waking Workspace Sentinel instantly.")
                asyncio.create_task(workspace_sentinel_immediate())
        except Exception as e:
            logger.error(f"Error handling DataChannel packet: {e}")

    async def workspace_sentinel_immediate():
        if shutdown_event.is_set():
            return

        if latest_screen_frame is None:
            if session_mode == "TUTOR":
                await notify_live_agent(
                    "RUN_CODE_WITHOUT_SCREEN",
                    "Screen share is not available yet. Ask the user to enable full screen sharing so you can assist with on-screen tasks."
                )
            else:
                await notify_live_agent(
                    "RUN_CODE_WITHOUT_SCREEN",
                    "The candidate clicked Run Code, but screen share is not available yet. Ask them to ensure full screen sharing is enabled, then click Run Code again."
                )
            return

        if session_mode == "TUTOR":
            prompt = "Look at the current screen and provide one concise assistant recommendation for coding/email/browsing/productivity. If nothing actionable is present, reply 'OK'."
        else:
            prompt = "Look at the code on the screen. The user just clicked run. Identify any bugs concisely. If fine, reply 'Your code looks good!'"
        result = await analyze_frame_with_gemini(prompt, latest_screen_frame)
        if result != "OK" and result != "":
            if session_mode == "TUTOR":
                await notify_live_agent(
                    "WORKSPACE_ALERT_IMMEDIATE",
                    f"ASSISTANT SCREEN INSIGHT: {result}. Reply as an assistant with direct, actionable help."
                )
            else:
                await notify_live_agent(
                    "WORKSPACE_ALERT_IMMEDIATE",
                    f"WORKSPACE ALERT: {result}. Give a concise interview-style hint and stay in interviewer role."
                )

    async def finalize_interview(reason: str):
        nonlocal has_finalized
        async with finalize_lock:
            if has_finalized:
                return
            has_finalized = True

        logger.info(f"Finalizing interview session. reason={reason}")
        shutdown_event.set()

        if proctor_task:
            proctor_task.cancel()
        workspace_task.cancel()
        for task in capture_tasks:
            task.cancel()

        final_transcript = "\n".join(transcript_builder).strip()
        logger.info(f"Final transcript entries: {len(transcript_builder)}")
        if final_transcript:
            logger.info(f"Final transcript preview:\n{final_transcript[:2000]}")

        report_triggered = await post_transcript_to_java(interview_id, candidate_code, final_transcript)
        if report_triggered:
            completed = await mark_interview_completed_in_java(interview_id)
            if completed:
                logger.info("Finalize complete: report trigger accepted and completion fallback requested.")
            else:
                logger.error("Finalize partial: report trigger accepted but completion fallback request failed.")
        else:
            logger.error("Finalize incomplete: report trigger failed; interview will not be marked completed without a report.")

    # ==========================================
    # 5. THE HANDBACK (ROOM DISCONNECT)
    # ==========================================
    @ctx.room.on("disconnected")
    def on_disconnected():
        logger.info("Room disconnected event received.")
        asyncio.create_task(finalize_interview("room_disconnected"))

    @ctx.room.on("participant_disconnected")
    def on_participant_disconnected(participant: rtc.RemoteParticipant):
        logger.info(f"Participant disconnected: {participant.identity}")
        asyncio.create_task(finalize_interview("participant_disconnected"))

    async def post_transcript_to_java(i_id, c_code, transcript_text) -> bool:
        url = f"{os.getenv('JAVA_BACKEND_URL')}/api/internal/reports/trigger"
        req_headers = {"X-Internal-Token": os.getenv("INTERNAL_PYTHON_SECRET"), "Content-Type": "application/json"}
        safe_transcript = transcript_text if transcript_text else "[NO_TRANSCRIPT_CAPTURED]"

        payload = {
            "interviewId": i_id,
            "accessCode": c_code,
            "transcript": safe_transcript,
            "finalCode": "Captured via AI Vision"
        }
        try:
            async with aiohttp.ClientSession() as client_session:
                async with client_session.post(url, headers=req_headers, json=payload) as post_resp:
                    response_text = await post_resp.text()
                    logger.info(f"Java Webhook Response: {post_resp.status}; body={response_text[:300]}")
                    return 200 <= post_resp.status < 300
        except Exception as e:
            logger.error(f"Failed posting transcript to Java: {e}")
            return False

    async def mark_interview_completed_in_java(i_id: str) -> bool:
        url = f"{os.getenv('JAVA_BACKEND_URL')}/api/internal/reports/interviews/{i_id}/status/completed"
        req_headers = {"X-Internal-Token": os.getenv("INTERNAL_PYTHON_SECRET")}
        try:
            async with aiohttp.ClientSession() as client_session:
                async with client_session.put(url, headers=req_headers) as put_resp:
                    response_text = await put_resp.text()
                    logger.info(f"Mark completed response: {put_resp.status}; body={response_text[:300]}")
                    return 200 <= put_resp.status < 300
        except Exception as e:
            logger.error(f"Failed requesting interview completion fallback: {e}")
            return False


if __name__ == "__main__":
    cli.run_app(WorkerOptions(entrypoint_fnc=entrypoint))