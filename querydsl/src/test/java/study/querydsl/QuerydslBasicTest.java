package study.querydsl;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.QueryResults;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;
import study.querydsl.dto.MemberDto;
import study.querydsl.dto.QMemberDto;
import study.querydsl.dto.UserDto;
import study.querydsl.entity.Member;
import study.querydsl.entity.QMember;
import study.querydsl.entity.QTeam;
import study.querydsl.entity.Team;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceUnit;
import javax.persistence.TypedQuery;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static study.querydsl.entity.QMember.*;
import static study.querydsl.entity.QTeam.*;

@SpringBootTest
@Transactional
public class QuerydslBasicTest {

    @Autowired
    EntityManager em;

    JPAQueryFactory queryFactory;

    @BeforeEach // 테스트 실행 전 데이터를 먼저 셋팅
    public void before() {
        Team teamA = new Team("teamA");
        Team teamB = new Team("teamB");
        em.persist(teamA);
        em.persist(teamB);

        Member member1 = new Member("member1", 10, teamA);
        Member member2 = new Member("member2", 20, teamA);

        Member member3 = new Member("member3", 30, teamB);
        Member member4 = new Member("member4", 40, teamB);
        em.persist(member1);
        em.persist(member2);
        em.persist(member3);
        em.persist(member4);
    }
    
    //querydsl사용 전 jpql예제
    @Test
    public void startJPQL() {
        //member1 조회
        Member findMember = em.createQuery("select m " +
                        "from Member m " +
                        "where m.username = :username", Member.class)
                .setParameter("username", "member1")
                .getSingleResult();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }
    
    //위 예제 querydsl로 변형
    @Test
    public void startQuerydsl() {
        queryFactory = new JPAQueryFactory(em); // 항상 JPAQueryFactory 생성 후 시작
        //gradle에서 Task -> other -> compileQuerydsl 실행

        Member findMember = queryFactory
                .select(member)
                .from(member)
                .where(member.username.eq("member1"))    //자동으로 파라미터 바인딩
                .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    //검색 조건 쿼리
    @Test
    public void search() {
        queryFactory = new JPAQueryFactory(em);

        Member findMember = queryFactory
                .selectFrom(member)
                .where(member.username.eq("member1")
                        .and(member.age.eq(10)))
                .fetchOne();    // 단건 조회

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void searchAndParam() {
        queryFactory = new JPAQueryFactory(em);

        Member findMember = queryFactory
                .selectFrom(member)
                .where(member.username.eq("member1"),   // ','는 and랑 동일하다
                        (member.age.eq(10)))
                .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    //조회 결과 종류
    @Test
    public void resultFetchTest() {
        queryFactory = new JPAQueryFactory(em);

        List<Member> fetch = queryFactory
                .selectFrom(member)
                .fetch();

        Member fetchOne = queryFactory
                .selectFrom(member)
                .fetchOne();

        Member fetchFirst = queryFactory
                .selectFrom(member)
                .fetchFirst();

        QueryResults<Member> results = queryFactory
                .selectFrom(member)
                .fetchResults();

        long total = results.getTotal(); // 총 갯수
        List<Member> content = results.getResults(); // 내용을 꺼냄

        long onlyCount = queryFactory
                .selectFrom(member)
                .fetchCount();
    }

    //정렬
    @Test
    public void sort() {
        queryFactory = new JPAQueryFactory(em);

        em.persist(new Member(null, 100));
        em.persist(new Member("member5", 100));
        em.persist(new Member("member6", 100));

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.eq(100))
                .orderBy(member.age.desc(), member.username.asc().nullsLast())
                .fetch();

        Member member5 = result.get(0);
        Member member6 = result.get(1);
        Member memberNull = result.get(2);
        assertThat(member5.getUsername()).isEqualTo("member5");
        assertThat(member6.getUsername()).isEqualTo("member6");
        assertThat(memberNull.getUsername()).isNull();

    }
    
    //페이징
    @Test
    public void paging1() {
        queryFactory = new JPAQueryFactory(em);
        QueryResults<Member> queryResults = queryFactory
                .selectFrom(member)
                .orderBy(member.username.desc())
                .offset(1)  // 시작 위치 1 지정
                .limit(2)   // 2 제한
                .fetchResults();
        
        assertThat(queryResults.getTotal()).isEqualTo(4);
        assertThat(queryResults.getLimit()).isEqualTo(2);
        assertThat(queryResults.getOffset()).isEqualTo(1);
        assertThat(queryResults.getResults().size()).isEqualTo(2);
    }

    @Test
    public void aggregation() {
        queryFactory = new JPAQueryFactory(em);

        List<Tuple> fetch = queryFactory.select(
                        member.count(),
                        member.age.sum(),
                        member.age.avg(),
                        member.age.max(),
                        member.age.min()
                )
                .from(member)
                .fetch();

        Tuple tuple = fetch.get(0);
        assertThat(tuple.get(member.count())).isEqualTo(4);
        assertThat(tuple.get(member.age.sum())).isEqualTo(40);
        assertThat(tuple.get(member.age.avg())).isEqualTo(10);
        assertThat(tuple.get(member.age.max())).isEqualTo(10);
        assertThat(tuple.get(member.age.min())).isEqualTo(10);
    }

    /** 팀 이름과 각 팀의 평균 연령 */
    @Test
    public void group() {
        queryFactory = new JPAQueryFactory(em);

        List<Tuple> result = queryFactory
                .select(team.name, member.age.avg())
                .from(member)
                .join(member.team, team)
                .groupBy(team.name)
                .fetch();
        Tuple teamA = result.get(0);
        Tuple teamB = result.get(1);

        assertThat(teamA.get(team.name)).isEqualTo("teamA");
        assertThat(teamA.get(member.age.avg())).isEqualTo(15);

        assertThat(teamB.get(team.name)).isEqualTo("teamB");
        assertThat(teamB.get(member.age.avg())).isEqualTo(35);
    }

    /** 팀A에 소속된 모든 회원을 찾아라 */
    @Test
    public void join() {
        queryFactory = new JPAQueryFactory(em);

        List<Member> result = queryFactory
                .selectFrom(member)
                .join(member.team, team)
                .where(team.name.eq("teamA"))
                .fetch();
        assertThat(result).extracting("username").containsExactly("member1", "member2");
    }

    //회원 이름이 팀 이름과 같은 회원 조회
    //세타 조인 ( 막 조인 )
    @Test
    public void theta_join() {
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));

        queryFactory = new JPAQueryFactory(em);

        List<Member> result = queryFactory
                .select(member)
                .from(member, team)
                .where(member.username.eq(team.name))
                .fetch();

        assertThat(result).extracting("username").containsExactly("teamA", "teamB");
    }

    // 회원과 팀을 조인하면서, 팀 이름이 teamA인 팀만 조인하고, 회원은 모두 조회
    // -> 회원은 모두 조회하면서 teamA인 애들만 값을 표시하고 아니면 null로 표시
    // -> left join사용하면 teamA아니면 null로 표시됨
    // jpql = select m, t from Member m left join m.team t on t.name = 'teamA'
    @Test
    public void join_on_filtering() {
        queryFactory = new JPAQueryFactory(em);
        
        // inner join일 땐 where절 사용
        // 외부조인이 필요하면 on 사용 ( ex : left join )
        List<Tuple> result = queryFactory
                .select(member, team)
                .from(member)
                .leftJoin(member.team, team)
                .on(team.name.eq("teamA"))
//                .where(team.name.eq("teamA"))
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    /** 연관관계 없는 엔티티 외부 조인
    회원의 이름이 팀 이름과 같은 대상 외부 조인 */
    @Test
    public void join_on_no_relation() {
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));
        em.persist(new Member("teamC"));
        queryFactory = new JPAQueryFactory(em);


        //원래는 leftJoin(member.team) 이렇게 쓰는게 기본이지만
        //막조인이라 leftJoin(team)
        List<Tuple> result = queryFactory
                .select(member, team)
                .from(member)
                .leftJoin(team).on(member.username.eq(team.name))
                .fetch();
        //team을 대상테이블로 지정하고 팀이름과 회원이름이 일치하는 것만 조회
        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    @PersistenceUnit
    EntityManagerFactory emf;

    /** 페치조인 적용 x */
    @Test
    public void fetchJoinNo() {
        em.flush();
        em.clear();

        queryFactory = new JPAQueryFactory(em);

        Member findMember = queryFactory
                .selectFrom(member)
                .where(member.username.eq("member1"))
                .fetchOne();
        //로딩이 안됬을때를 상황으로 검사
        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
        assertThat(loaded).as("페치 조인 미적용").isFalse();
    }

    /** 페치조인 적용 o */
    @Test
    public void fetchJoinUser() {
        em.flush();
        em.clear();

        queryFactory = new JPAQueryFactory(em);

        Member findMember = queryFactory
                .selectFrom(member)
                .join(member.team, team).fetchJoin()
                .where(member.username.eq("member1"))
                .fetchOne();
        //페치조인으로 인한 로딩 체크
        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
        assertThat(loaded).as("페치 조인 미적용").isTrue();
    }

    /** 나이가 가장 많은 회원 조회 with SuqQuery*/
    @Test
    public void subQuery() {
        queryFactory = new JPAQueryFactory(em);

        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.eq(
                        JPAExpressions.select(memberSub.age.max())
                                .from(memberSub)
                ))
                .fetch();
        assertThat(result).extracting("age").containsExactly(40);
    }

    
    /** 나이가 평균 이상인 회원 ( goe >= ) with SubQuery*/
    @Test
    public void subQueryGoe() {
        queryFactory = new JPAQueryFactory(em);

        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.goe(
                        JPAExpressions.select(memberSub.age.avg())
                                .from(memberSub)
                ))
                .fetch();
        assertThat(result).extracting("age").containsExactly(30, 40);
    }

    /** 회원 이름과 평균나이를 조회 with SubQuery */
    @Test
    public void selectSubQuery() {
        queryFactory = new JPAQueryFactory(em);

        QMember memberSub = new QMember("memberSub");

        List<Tuple> result = queryFactory
                .select(member.username,
                        JPAExpressions
                                .select(memberSub.age.avg())
                                .from(memberSub))
                .from(member)
                .fetch();
        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }

        /**
         * from절의 서브쿼리는 지원하지 않는다.
         * 해결방안
           - 서브쿼리를 join으로 변경한다.
           - 애플리케이션에서 쿼리를 2번 분리하여 실행한다.
           - nativeSQL 사용
         * **/
    }

    /** case 기본 */
    @Test
    public void basicCase() {
        queryFactory = new JPAQueryFactory(em);

        List<String> result = queryFactory
                .select(member.age
                        .when(10).then("열살")
                        .when(20).then("스무살")
                        .otherwise("기타"))
                .from(member)
                .fetch();
        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    /** case 심화 */
    @Test
    public void complexCase() {
        queryFactory = new JPAQueryFactory(em);

        List<String> result = queryFactory
                .select(new CaseBuilder()
                        .when(member.age.between(0, 20)).then("0~20살")
                        .when(member.age.between(21, 30)).then("21~30살")
                        .otherwise("기타"))
                .from(member)
                .fetch();
        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    /** 상수 */
    @Test
    public void constant() {
        queryFactory = new JPAQueryFactory(em);
        List<Tuple> result = queryFactory
                .select(member.username, Expressions.constant("A"))
                .from(member)
                .fetch();
        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    /** 문자 더하기 concat */
    @Test
    public void concat() {
        queryFactory = new JPAQueryFactory(em);

        //username_{age}
        List<String> result = queryFactory
                .select(member.username.concat("_").concat(member.age.stringValue()))
                .from(member)
                .where(member.username.eq("member1"))
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    /** 중급 문법 */

    /** projection - select 대상 지정 */

    /** 반환 타입이 1개일 때 */
    @Test
    public void simpleProjection() {
        queryFactory = new JPAQueryFactory(em);


        List<String> result = queryFactory
                .select(member.username)
                .from(member)
                .fetch();
        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    /** 반환타입이 여러개일 때는 튜플 */
    @Test
    public void tupleProjection() {
        queryFactory = new JPAQueryFactory(em);

        List<Tuple> result = queryFactory
                .select(member.username, member.age)
                .from(member)
                .fetch();

        for (Tuple tuple : result) {
            String username = tuple.get(member.username);
            Integer age = tuple.get(member.age);
            System.out.println("username = " + username);
            System.out.println("age = " + age);
        }

        /**
         튜플은 왠만하면 repository 안에서만 사용하는 것이 좋다.
         바깥 계층으로 내보내는 것은 DTO 사용
         **/
    }

    /** jpql에서 dto 사용 */
    @Test
    public void findDtoByJPQL() {
        List<MemberDto> result = em.createQuery("select " +
                "new study.querydsl.dto.MemberDto(m.username, m.age) " +
                "from Member m", MemberDto.class)
                .getResultList();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    /** querydsl에서 dto 사용 Setter */
    @Test
    public void findDtoBySetter() {
        queryFactory = new JPAQueryFactory(em);

        /** query dsl의 마법 **/
        List<MemberDto> result = queryFactory
                    .select(Projections.bean(MemberDto.class,
                            member.username,
                            member.age))
                    .from(member)
                    .fetch();
        //주의 - DTO에 기본 생성자 필수

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    /** querydsl에서 dto 사용 Field */
    @Test
    public void findDtoByField() {
        queryFactory = new JPAQueryFactory(em);

        /** query dsl의 마법 **/
        List<MemberDto> result = queryFactory
                .select(Projections.fields(MemberDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();
        //주의 - DTO에 기본 생성자 필수

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    /** querydsl에서 dto 사용 Constructor */
    @Test
    public void findDtoByConstructor() {
        queryFactory = new JPAQueryFactory(em);

        /** query dsl의 마법 **/
        List<MemberDto> result = queryFactory
                .select(Projections.constructor(MemberDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();
        //주의 - DTO에 기본 생성자 필수

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    /** querydsl에서 dto 사용 필드 값 일치시키기 **/
    @Test
    public void findUserDto() {
        queryFactory = new JPAQueryFactory(em);

        QMember memberSub = new QMember("memberSub");

        /** query dsl의 마법 **/
        List<UserDto> result = queryFactory
                .select(Projections.fields(UserDto.class,
                        member.username.as("name"), // as사용하여 field 값과 일치하게 변환

                        ExpressionUtils.as(JPAExpressions   //서브쿼리에 이름을 주고 싶을 때 ExpressionUtils로 지정
                                .select(memberSub.age.max())
                                .from(memberSub), "age")
                ))
                .from(member)
                .fetch();
        //주의 - DTO에 기본 생성자 필수

        for (UserDto userDto : result) {
            System.out.println("userDto = " + userDto);
        }
    }

    /** @QueryProjection 어노테이션을 이용한 Dto 처리 */
    @Test
    public void findDtoByQueryProjection() {
        queryFactory = new JPAQueryFactory(em);

        List<MemberDto> result = queryFactory
                .select(new QMemberDto(member.username, member.age))    //깔끔하게 new 생성자로 처리
                .from(member)
                .fetch();
        //장점 - 런타임 전에 오류를 잡아낼 수 있다.
        //단점 - Dto를 따로 생성해야한다,
        //    - Dto가 QueryProjection에 의존하고 있다는 점
        
        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    /** 동적쿼리로 검색조건 설정 */
    @Test
    public void dynamicQuery_BooleanBuilder() {
        String usernameParam = null;
        Integer ageParam = 10;

        List<Member> result = searchMember1(usernameParam, ageParam);
        assertThat(result.size()).isEqualTo(1);
    }

    /** 동적 쿼리 메소드 */
    private List<Member> searchMember1(String usernameCond, Integer ageCond) {
        queryFactory = new JPAQueryFactory(em);

        //초기값 설정도 가능 ( usernameCond는 null이면 안됨 )
        BooleanBuilder builder = new BooleanBuilder(member.username.eq(usernameCond));

        // null이 아니면 builder조건에 값을 추가
        if(usernameCond != null)
            builder.and(member.username.eq(usernameCond));

        if(ageCond != null)
            builder.and(member.age.eq(ageCond));

        return queryFactory
                .selectFrom(member)
                .where(builder)
                .fetch();
    }

    /** BooleanBuilder 보다 깔끔하게 동적 쿼리 처리 */
    @Test
    public void dinamicQuery_WhereParam() {
        String usernameParam = null;
        Integer ageParam = 10;

        List<Member> result = searchMember2(usernameParam, ageParam);
        assertThat(result.size()).isEqualTo(1);
    }

    /** 동적 쿼리 메소드 2 */
    private List<Member> searchMember2(String usernameCond, Integer ageCond) {
        queryFactory = new JPAQueryFactory(em);

        return queryFactory
                .selectFrom(member)
//              .where(allEq(usernameCond, ageCond))   밑 코드 한번에 처리
                .where(usernameEq(usernameCond), ageEq(ageCond))  // where절에 null값이 존재할 경우 무시됨
                .fetch();
    }

    private BooleanExpression ageEq(Integer ageCond) {
        return ageCond == null ? null : member.age.eq(ageCond);
    }

    private BooleanExpression usernameEq(String usernameCond) {
        return usernameCond == null ? null : member.username.eq(usernameCond);
    }

    /** 위 두개 메소드 합치기 */
    private BooleanExpression allEq(String usernameCond, Integer ageCond) {
        return usernameEq(usernameCond).and(ageEq(ageCond));
    }

    /** 수정, 삭제 배치 쿼리 ( bulk 연산 and 문제점) */
    @Test
    @Rollback(value = false)
    public void bulkUpdate() {
        queryFactory = new JPAQueryFactory(em);

        /** bulk 연산은 영속성 컨텍스트를 무시하고 바로 update 쿼리문을 날려서 문제가 있다. */

        /** before
            member1 = 10 -> DB member1
            member2 = 20 -> DB member2
            member3 = 30 -> DB member3
            member4 = 40 -> DB member4
         */

        long count = queryFactory
                .update(member)
                .set(member.username, "비회원")    // 이름을 비회원으로 변경시킨다
                .where(member.age.lt(28))   // 28살 미만이면  age < 28  loe는 age <= 28
                .execute();

        //해결법
        em.flush(); 
        em.clear();

        /** after
            member1 = 10 -> DB 비회원
            member2 = 20 -> DB 비회원
            member3 = 30 -> DB member3
            member4 = 40 -> DB member4
         */

        //이렇게 될경우 영속성 컨텍스트에 있는 값을 가져오기 때문에
        // DB값과 일치하지 않는 결과가 나온다.
        List<Member> result = queryFactory
                .selectFrom(member)
                .fetch();

        for (Member member1 : result) {
            System.out.println("member1 = " + member1);
        }
    }

    /** bulk 숫자 연산 **/
    @Test
    public void bulkAdd() {
        queryFactory = new JPAQueryFactory(em);

        long count = queryFactory
                .update(member)
                .set(member.age, member.age.add(1)) // 곱하기 = multiply
                .execute();
    }

    /** bulk연산 삭제 **/
    @Test
    public void bulkDelete() {
        queryFactory = new JPAQueryFactory(em);

        long count = queryFactory
                .delete(member)
                .where(member.age.gt(18))   // age > 18
                .execute();
    }

    /** SQL Function을 이용하여 조회값 문자 변형 */
    @Test
    public void sqlFunction() {
        queryFactory = new JPAQueryFactory(em);

        List<String> result = queryFactory
                .select(Expressions.stringTemplate(
                        "function('replace', {0}, {1}, {2})",
                        member.username, "member", "M"))
                .from(member)
                .fetch();
        
        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    @Test
    public void sqlFunction2() {
        queryFactory = new JPAQueryFactory(em);

        List<String> result = queryFactory
                .select(member.username)
                .from(member)
//                .where(member.username.eq(
//                        Expressions.stringTemplate("function('lower', {0})", member.username)))
                .where(member.username.eq(member.username.lower()))
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }
}
