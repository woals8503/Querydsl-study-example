package study.querydsl.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import study.querydsl.entity.Member;

import java.util.List;

public interface MemberRepository extends JpaRepository<Member, Long>, MemberRepositoryCustom {
    //select m from Member m where m.username = ?   메소드 이름이 중요
    List<Member> findByUsername(String username);
}
