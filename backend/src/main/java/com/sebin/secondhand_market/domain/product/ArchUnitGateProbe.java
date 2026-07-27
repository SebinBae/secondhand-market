package com.sebin.secondhand_market.domain.product;

import com.sebin.secondhand_market.domain.user.repository.UserRepository;

/**
 * 머지 게이트 검증용 임시 클래스. **머지하지 않는다.**
 *
 * <p>product 도메인에서 user 도메인의 repository를 직접 참조해 ArchUnit 규칙
 * {@code product는_user의_repository를_직접_쓰지_않는다} 를 일부러 위반한다.
 *
 * <p>목적: required status check이 실패했을 때 실제로 머지가 막히는지 확인한다.
 * 체크가 도는 것과 머지가 막히는 것은 다른 문제이므로 실패 경로를 직접 관측한다.
 * 확인이 끝나면 이 PR은 머지하지 않고 닫는다.
 */
class ArchUnitGateProbe {

  private final UserRepository userRepository;

  ArchUnitGateProbe(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  boolean exists(java.util.UUID id) {
    return userRepository.existsById(id);
  }
}
