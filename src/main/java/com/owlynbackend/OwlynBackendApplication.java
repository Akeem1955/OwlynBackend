package com.owlynbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication
@EnableAspectJAutoProxy
public class OwlynBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(OwlynBackendApplication.class, args);
	}

}
