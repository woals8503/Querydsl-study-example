package study.querydsl.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import study.querydsl.dto.MemberSearchCondition;
import study.querydsl.dto.MemberTeamDto;

import java.util.List;

public interface MemberRepositoryCustom {
    /** 스프링 데이터 JPA를 사용하면서 내가 직접 구현해서 사용하고 싶다면 이름을 직접 지정 후 구현*/
    List<MemberTeamDto> search(MemberSearchCondition condition);
    
    //단순
    Page<MemberTeamDto> searchPageSimple(MemberSearchCondition condition, Pageable pageable);
    
    //복잡
    Page<MemberTeamDto> searchPageComplex(MemberSearchCondition condition, Pageable pageable);
}
