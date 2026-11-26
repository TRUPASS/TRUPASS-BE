package com.trupass.core.domain.user.repository;

import com.trupass.core.domain.user.entity.AgreementHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgreementHistoryRepository extends JpaRepository<AgreementHistory, Long> {

  /** 한 회원의 기록을 쌓인 순서대로 */
  List<AgreementHistory> findByUserIdOrderByIdAsc(Long userId);
}
