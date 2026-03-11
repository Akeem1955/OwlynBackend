package com.owlynbackend.config.security;


import com.owlynbackend.internal.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import java.util.Optional;


@Component
public class OwlynUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;


    @Autowired
    public OwlynUserDetailsService(UserRepository userRepository){
        System.out.println("In JdbcUserDetailsService");
        this.userRepository=userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Optional<com.owlynbackend.internal.model.User> user = userRepository.findByEmail(username);
        if(user.isPresent()){
            return User.withUsername(user.get().getEmail())
                    .password(user.get().getPassword())
                    .build();
        }
        throw new UsernameNotFoundException(username);
    }
}
