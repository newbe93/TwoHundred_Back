package org.duckdns.bidbuy.app.user.service;

import org.duckdns.bidbuy.app.user.repository.UserRepository;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {
  private final UserRepository userRepository;

}
