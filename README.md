# 휴양소_예약시스템
![image](https://user-images.githubusercontent.com/85722729/124409014-75656b80-dd82-11eb-9a56-672bc90270ad.jpg)

# Table of contents
- [휴양소_예약시스템](#---)
  - [서비스 시나리오](#서비스-시나리오)
  - [체크포인트](#체크포인트)
  - [분석/설계](#분석설계)
  - [구현:](#구현-)
    - [DDD 의 적용](#ddd-의-적용)
    - [폴리글랏 퍼시스턴스](#폴리글랏-퍼시스턴스)
    - [폴리글랏 프로그래밍](#폴리글랏-프로그래밍)
    - [동기식 호출 과 Fallback 처리](#동기식-호출-과-Fallback-처리)
    - [비동기식 호출 과 Eventual Consistency](#비동기식-호출-과-Eventual-Consistency)
  - [운영](#운영)
    - [CI/CD 설정](#cicd설정)
    - [동기식 호출 / 서킷 브레이킹 / 장애격리](#동기식-호출-서킷-브레이킹-장애격리)
    - [오토스케일 아웃](#오토스케일-아웃)
    - [무정지 재배포](#무정지-재배포)

# 서비스 시나리오
- 기능적 요구사항(전체)
1. 휴양소 관리자는 휴양소를 등록한다.
2. 고객이 휴양소를 선택하여 예약한다.
4. 고객이 예약한 휴양소를 결제한다.(팀과제에서 제외)
5. 예약이 확정되어 휴양소는 예약불가 상태로 바뀐다.
6. 바우처가 고객에게 발송된다. (팀과제에서 제외)
7. 고객이 확정된 예약을 취소할 수 있다.
8. 주문이 취소되면 바우처가 비활성화 된다.(팀과제에서 제외)
9. 휴양소는 예약가능 상태로 바뀐다.
10. 고객은 휴양소 예약 정보를 확인 할 수 있다.

- 비기능적 요구사항
1. 트랜잭션
    1. 리조트 상태가 예약 가능상태가 아니면 아예 예약이 성립되지 않아야 한다  Sync 호출 
1. 장애격리
    1. 결제/바우처/마이페이지 기능이 수행되지 않더라도 주문은 365일 24시간 받을 수 있어야 한다.  Async (event-driven), Eventual Consistency
    1. 예약시스템이 과중되면 사용자를 잠시동안 받지 않고 잠시후에 하도록 유도한다.  Circuit breaker, fallback
1. 성능
    1. 고객이 자신의 예약 상태를 확인할 수 있도록 마이페이지가 제공 되어야 한다.  CQRS

# 분석/설계

## AS-IS 조직 (Horizontally-Aligned)

  ![image](https://user-images.githubusercontent.com/487999/79684144-2a893200-826a-11ea-9a01-79927d3a0107.png)

## TO-BE 조직 (Vertically-Aligned)

  ![image](https://user-images.githubusercontent.com/85722729/125151796-b9da7800-e183-11eb-8aa1-75206e01d5d1.png)


## Event Storming 결과
* MSAEz 로 모델링한 이벤트스토밍 결과: http://www.msaez.io/#/storming/Rk7HwUkaXvX3C7EArWnMpnml68j1/share/4992a1785df30cc101b0e57f3400fa2d


### 이벤트 도출
![image](https://user-images.githubusercontent.com/85722729/125151784-9dd6d680-e183-11eb-85d9-283209715fbd.png)

### 액터, 커맨드 부착하여 읽기 좋게
![image](https://user-images.githubusercontent.com/85722729/125151789-ad561f80-e183-11eb-9e57-05ed86763be9.png)

### 어그리게잇으로 묶기
![image](https://user-images.githubusercontent.com/85722729/125151790-afb87980-e183-11eb-98fa-059b849dc46e.png)

### 바운디드 컨텍스트로 묶기

![image](https://user-images.githubusercontent.com/85722729/125151791-b1823d00-e183-11eb-8575-97c163ccf14e.png)

### 폴리시 부착 (괄호는 수행주체, 폴리시 부착을 둘째단계에서 해놔도 상관 없음. 전체 연계가 초기에 드러남)

![image](https://user-images.githubusercontent.com/85722729/125151793-b2b36a00-e183-11eb-814a-1fd91a06787b.png)

### 폴리시의 이동과 컨텍스트 매핑 (점선은 Pub/Sub, 실선은 Req/Resp)

![image](https://user-images.githubusercontent.com/85722729/125151794-b47d2d80-e183-11eb-9af6-3f8d26a1eddc.png)

### 완성된 모형

![image](https://user-images.githubusercontent.com/85722729/124701475-9f0cc700-df29-11eb-9eb9-dc0c74df7049.png)

- View Model 추가
- 도메인 서열
  - Core : reservation
  - Supporting : resort
  - General : payment, voucher, mypage

## 헥사고날 아키텍처 다이어그램 도출
    
![image](https://user-images.githubusercontent.com/85722729/125151798-bcd56880-e183-11eb-876b-074a02d94116.png)



    - Chris Richardson, MSA Patterns 참고하여 Inbound adaptor와 Outbound adaptor를 구분함
    - 호출관계에서 PubSub 과 Req/Resp 를 구분함
    - 서브 도메인과 바운디드 컨텍스트의 분리:  각 팀의 KPI 별로 아래와 같이 관심 구현 스토리를 나눠가짐


# 구현:

분석/설계 단계에서 도출된 헥사고날 아키텍처에 따라, 각 BC별로 대변되는 마이크로 서비스들을 스프링부트로 구현하였다. 구현한 각 서비스를 로컬에서 실행하는 방법은 아래와 같다 (각자의 포트넘버는 8081 ~ 808n 이다)

## 시나리오에 따른 처리
1. 휴양소 관리자는 휴양소를 등록한다.
```sh
http a958945a89af4402894a5f7563b42983-1227591683.ap-northeast-2.elb.amazonaws.com:8080/resorts resortName="Jeju" resortType="Hotel" resortPrice=100000 resortStatus="Available" resortPeriod="7/23~25"
http a958945a89af4402894a5f7563b42983-1227591683.ap-northeast-2.elb.amazonaws.com:8080/resorts resortName="Seoul" resortType="Hotel" resortPrice=100000 resortStatus="Available" resortPeriod="7/23~25"
http a958945a89af4402894a5f7563b42983-1227591683.ap-northeast-2.elb.amazonaws.com:8080/resorts
```
![image](https://user-images.githubusercontent.com/85722851/124924911-e2a12700-e036-11eb-8ed3-6bb5d27a1f7d.png)

2. 고객이 휴양소를 선택하여 예약한다.
```sh
http a958945a89af4402894a5f7563b42983-1227591683.ap-northeast-2.elb.amazonaws.com:8080/reservations resortId=2 memberName="sim sang joon"
```
![image](https://user-images.githubusercontent.com/85722851/124925247-357ade80-e037-11eb-877b-37d53c3c71ed.png)

3. 예약이 확정되어 휴양소는 예약불가 상태로 바뀐다.
```sh
http a958945a89af4402894a5f7563b42983-1227591683.ap-northeast-2.elb.amazonaws.com:8080/resorts/2
```
![image](https://user-images.githubusercontent.com/85722851/124925427-62c78c80-e037-11eb-8903-d57d1f840afc.png)

4. 고객이 확정된 예약을 취소할 수 있다.
```sh
http PATCH a958945a89af4402894a5f7563b42983-1227591683.ap-northeast-2.elb.amazonaws.com:8080/reservations/1 resortStatus="Cancelled"
```
![image](https://user-images.githubusercontent.com/85722851/124925543-81c61e80-e037-11eb-835d-9f34f1418a01.png)

5. 휴양소는 예약 가능상태로 바뀐다.
```sh
http a958945a89af4402894a5f7563b42983-1227591683.ap-northeast-2.elb.amazonaws.com:8080/resorts/2
```
![image](https://user-images.githubusercontent.com/85722851/124925643-9e625680-e037-11eb-90ad-24f8e5264d87.png)

6. 고객은 휴양소 예약 정보를 확인 할 수 있다.
```sh
http a958945a89af4402894a5f7563b42983-1227591683.ap-northeast-2.elb.amazonaws.com:8080/myPages
```
![image](https://user-images.githubusercontent.com/85722851/124925795-c81b7d80-e037-11eb-912e-128c63f7d9d2.png)

## DDD 의 적용
- 위 이벤트 스토밍을 통해 식별된 Micro Service 전체 5개 중 3개를 구현하였으며 그 중 mypage는 CQRS를 위한 서비스이다.

|MSA|기능|port|URL|
| :--: | :--: | :--: | :--: |
|reservation| 예약정보 관리 |8081|http://localhost:8081/reservations|
|resort| 리조트 관리 |8082|http://localhost:8082/resorts|
|mypage| 예약내역 조회 |8083|http://localhost:8086/mypages|
|gateway| gateway |8088|http://localhost:8088|

## Gateway 적용
- API GateWay를 통하여 마이크로 서비스들의 진입점을 통일할 수 있다. 
다음과 같이 GateWay를 적용하였다.

```yaml
server:
  port: 8088
---
spring:
  profiles: default
  cloud:
    gateway:
      routes:
        - id: reservation
          uri: http://localhost:8081
          predicates:
            - Path=/reservations/** 
        - id: resort
          uri: http://localhost:8082
          predicates:
            - Path=/resorts/** 
        - id: mypage
          uri: http://localhost:8083
          predicates:
            - Path= /myPages/**
      globalcors:
        corsConfigurations:
          '[/**]':
            allowedOrigins:
              - "*"
            allowedMethods:
              - "*"
            allowedHeaders:
              - "*"
            allowCredentials: true
---
spring:
  profiles: docker
  cloud:
    gateway:
      routes:
        - id: reservation
          uri: http://reservation:8080
          predicates:
            - Path=/reservations/** 
        - id: resort
          uri: http://resort:8080
          predicates:
            - Path=/resorts/** 
        - id: mypage
          uri: http://mypage:8080
          predicates:
            - Path= /myPages/**
      globalcors:
        corsConfigurations:
          '[/**]':
            allowedOrigins:
              - "*"
            allowedMethods:
              - "*"
            allowedHeaders:
              - "*"
            allowCredentials: true
server:
  port: 8080
```
## 폴리글랏 퍼시스턴스
- CQRS 를 위한 mypage 서비스만 DB를 구분하여 적용함. 인메모리 DB인 hsqldb 사용.
```
<!-- 
    <dependency>
        <groupId>com.h2database</groupId>
        <artifactId>h2</artifactId>
        <scope>runtime</scope>
    </dependency>
-->
    <dependency>
        <groupId>org.hsqldb</groupId>
        <artifactId>hsqldb</artifactId>
        <version>2.4.0</version>
        <scope>runtime</scope>
    </dependency>
```
## CQRS & Kafka
- 타 마이크로서비스의 데이터 원본에 접근없이 내 서비스의 화면 구성과 잦은 조회가 가능하게 mypage에 CQRS 구현하였다.
- 모든 정보는 비동기 방식으로 발행된 이벤트(예약, 예약취소, 가능상태변경)를 수신하여 처리된다.

예약 실행

![image](https://user-images.githubusercontent.com/85722851/124925247-357ade80-e037-11eb-877b-37d53c3c71ed.png)

카프카 메시지

![image](https://user-images.githubusercontent.com/85722851/124926078-14ff5400-e038-11eb-86f9-5397eb39e319.png)
```bash
{"eventType":"ReservationRegistered","timestamp":"20210708122821","id":null,"resortId":2,"resortName":"Seoul","resortStatus":"Confirmed","resortType":"Hotel","resortPeriod":"7/23~25","resortPrice":100000.0,"memberName":"sim sang joon"}
{"eventType":"ResortStatusChanged","timestamp":"20210708122821","id":2,"resortName":"Seoul","resortStatus":"Not Available","resortType":"Hotel","resortPeriod":"7/23~25","resortPrice":100000.0}
{"eventType":"ReservationCanceled","timestamp":"20210708122846","id":1,"resortId":2,"resortName":"Seoul","resortStatus":"Cancelled","resortType":"Hotel","resortPeriod":"7/23~25","resortPrice":100000.0,"memberName":"sim sang joon"}
{"eventType":"ResortStatusChanged","timestamp":"20210708122846","id":2,"resortName":"Seoul","resortStatus":"Available","resortType":"Hotel","resortPeriod":"7/23~25","resortPrice":100000.0}
```

예약/예약취소 후 mypage 화면

![image](https://user-images.githubusercontent.com/85722851/124925795-c81b7d80-e037-11eb-912e-128c63f7d9d2.png)


## 동기식 호출 과 Fallback 처리

- 분석단계에서의 조건 중 하나로 예약(reservation)->리조트상태확인(resort) 간의 호출은 동기식 일관성을 유지하는 트랜잭션으로 처리하기로 하였다. 호출 프로토콜은 이미 앞서 Rest Repository 에 의해 노출되어있는 REST 서비스를 FeignClient를 이용하여 호출하였다

- 리조트서비스를 호출하기 위하여 Stub과 (FeignClient) 를 이용하여 Service 대행 인터페이스 (Proxy) 를 구현 

```java
# (reservation) ResortService.java

package resortreservation.external;

@FeignClient(name="resort", url="${feign.resort.url}")
public interface ResortService {
    
    @RequestMapping(method= RequestMethod.GET, value="/resorts/{id}", consumes = "application/json")
    public Resort getResortStatus(@PathVariable("id") Long id);

}
```

- 예약을 처리 하기 직전(@PrePersist)에 ResortSevice를 호출하여 서비스 상태와 Resort 세부정보도 가져온다.
```java
# Reservation.java (Entity)

    @PrePersist
    public void onPrePersist() throws Exception {
        resortreservation.external.Resort resort = new resortreservation.external.Resort();
       
        //Resort 서비스에서 Resort의 상태를 가져옴
        resort = ReservationApplication.applicationContext.getBean(resortreservation.external.ResortService.class)
            .getResortStatus(resortId);

        // 예약 가능상태 여부에 따라 처리
        if ("Available".equals(resort.getResortStatus())){
            this.setResortName(resort.getResortName());
            this.setResortPeriod(resort.getResortPeriod());
            this.setResortPrice(resort.getResortPrice());
            this.setResortType(resort.getResortType());
            this.setResortStatus("Confirmed");
            ReservationRegistered reservationRegistered = new ReservationRegistered();
            BeanUtils.copyProperties(this, reservationRegistered);
            reservationRegistered.publishAfterCommit();
        } else {
            throw new Exception("The resort is not in a usable status.");
        }
    }
```

- 동기식 호출에서는 호출 시간에 따른 타임 커플링이 발생하며, 시스템이 장애로 예약을 못받는다는 것을 확인
![image](https://user-images.githubusercontent.com/85722851/124935086-47ad4a80-e040-11eb-99d1-eb20f47f1eb8.png)


- 또한 과도한 요청시에 서비스 장애가 도미노 처럼 벌어질 수 있다. (서킷브레이커, 폴백 처리는 운영단계에서 설명한다.)




## 비동기식 호출 / 시간적 디커플링 / 장애격리 / 최종 (Eventual) 일관성 테스트
- 예약이 이루어진 후에 결제시스템에 결제요청과 마이페이지시스템에 이력을 보내는 행위는 동기식이 아니라 비 동기식으로 처리하여 예약이 블로킹 되지 않아도록 처리한다.
- 이를 위하여 예약기록을 남긴 후에 곧바로 예약완료가 되었다는 도메인 이벤트를 카프카로 송출한다(Publish)
 
```java
@Entity
@Table(name="Reservation_table")
public class Reservation {
 ...
    @PostPersist
    public void onPostPersist() throws Exception {
        ...
        ReservationRegistered reservationRegistered = new ReservationRegistered();
        BeanUtils.copyProperties(this, reservationRegistered);
        reservationRegistered.publishAfterCommit();
    }
}
```
- 결제시스템과 마이페이지시스템에서는 예약완료 이벤트에 대해서 이를 수신하여 자신의 정책을 처리하도록 PolicyHandler 를 구현한다

결제시스템(팀과제에서는 미구현)
```java

@Service
public class PolicyHandler{
    @Autowired PaymentRepository paymentRepository;

    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverReservationRegistered_PaymentRequestPolicy(@Payload ReservationRegistered reservationRegistered){

        if(!reservationRegistered.validate()) return;
        System.out.println("\n\n##### listener PaymentRequestPolicy : " + reservationRegistered.toJson() + "\n\n");
        // Logic 구성 // 
    }
}
```
마이페이지시스템
```java
@Service
public class MyPageViewHandler {

    @Autowired
    private MyPageRepository myPageRepository;

    @StreamListener(KafkaProcessor.INPUT)
    public void whenReservationRegistered_then_CREATE_1 (@Payload ReservationRegistered reservationRegistered) {
        try {

            if (!reservationRegistered.validate()) return;

            // view 객체 생성
            MyPage myPage = new MyPage();
            // view 객체에 이벤트의 Value 를 set 함
            myPage.setId(reservationRegistered.getId());
            myPage.setMemberName(reservationRegistered.getMemberName());
            myPage.setResortId(reservationRegistered.getResortId());
            myPage.setResortName(reservationRegistered.getResortName());
            myPage.setResortStatus(reservationRegistered.getResortStatus());
            myPage.setResortType(reservationRegistered.getResortType());
            myPage.setResortPeriod(reservationRegistered.getResortPeriod());
            myPage.setResortPrice(reservationRegistered.getResortPrice());
            // view 레파지 토리에 save
            myPageRepository.save(myPage);
        
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
```
- 예약 시스템은 결제시스템/마이페이지 시스템과 완전히 분리되어있으며, 이벤트 수신에 따라 처리되기 때문에, 결제시스템/마이시스템이 유지보수로 인해 잠시 내려간 상태라도 예야을 받는데 문제가 없다:
```bash
# 마이페이지 서비스는 잠시 셧다운 시키고 결제시스템은 현재 미구현

1.리조트입력
http localhost:8082/resorts resortName="Jeju" resortType="Hotel" resortPrice=100000 resortStatus="Available" resortPeriod="7/23~25"
http localhost:8082/resorts resortName="Seoul" resortType="Hotel" resortPrice=100000 resortStatus="Available" resortPeriod="7/23~25"

2.예약입력
http localhost:8081/reservations resortId=2 memberName="sim sang joon" 
http localhost:8081/reservations #예약 정상 처리 확인

3.마이페이지서비스 기동

4.마이페이지확인
http localhost:8083/myPages #정상적으로 마이페이지에서 예약 이력이 확인 됨

```


# 운영

## CI/CD 설정

각 구현체들은 각자의 source repository 에 구성되었고, 각 서비스별로 Docker로 빌드를 하여, Docker Hub에 등록 후 deployment.yaml, service.yml을 통해 EKS에 배포함.
- git에서 소스 가져오기
```bash
git clone https://github.com/simair77/resort_reservation.git
```
- 각서비스별 packege, build, github push 실행
```bash
cd resort #서비스별 폴더로 이동
mvn package -B -Dmaven.test.skip=true #패키지

docker build -t simair/resort:latest . #docker build
docker push simair/resort:latest       #docker push

kubectl apply -f resort/kubernetes/deployment.yml #AWS deploy 수행
kubectl apply -f resort/kubernetes/service.yaml.  #AWS service 등록

```
- Docker Hub Image
![image](https://user-images.githubusercontent.com/85722851/125154338-881ddd00-e194-11eb-8f76-f4b07d072313.png)

- 최종 Deploy완료
![image](https://user-images.githubusercontent.com/85722851/124926911-02d1e580-e039-11eb-881c-2f0822aaaee1.png)

## 동기식 호출 / 서킷 브레이킹 / 장애격리

* 서킷 브레이킹 프레임워크의 선택: Spring FeignClient + Hystrix 옵션을 사용하여 구현함

시나리오는 단말앱(app)-->결제(pay) 시의 연결을 RESTful Request/Response 로 연동하여 구현이 되어있고, 결제 요청이 과도할 경우 CB 를 통하여 장애격리.

- Hystrix 를 설정:  요청처리 쓰레드에서 처리시간이 610 밀리가 넘어서기 시작하여 어느정도 유지되면 CB 회로가 닫히도록 (요청을 빠르게 실패처리, 차단) 설정
```
# application.yml
feign:
  hystrix:
    enabled: true
    
hystrix:
  command:
    # 전역설정
    default:
      execution.isolation.thread.timeoutInMilliseconds: 610

```

- 피호출 서비스(결제:pay) 의 임의 부하 처리 - 400 밀리에서 증감 220 밀리 정도 왔다갔다 하게
```
# (pay) 결제이력.java (Entity)

    @PrePersist
    public void onPrePersist(){  //결제이력을 저장한 후 적당한 시간 끌기

        ...
        
        try {
            Thread.currentThread().sleep((long) (400 + Math.random() * 220));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
```

* 부하테스터 siege 툴을 통한 서킷 브레이커 동작 확인:
- 동시사용자 100명
- 60초 동안 실시

```
$ siege -c100 -t60S -r10 --content-type "application/json" 'http://localhost:8081/orders POST {"item": "chicken"}'

** SIEGE 4.0.5
** Preparing 100 concurrent users for battle.
The server is now under siege...

HTTP/1.1 201     0.68 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     0.68 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     0.70 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     0.70 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     0.73 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     0.75 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     0.77 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     0.97 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     0.81 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     0.87 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     1.12 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     1.16 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     1.17 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     1.26 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     1.25 secs:     207 bytes ==> POST http://localhost:8081/orders

* 요청이 과도하여 CB를 동작함 요청을 차단

HTTP/1.1 500     1.29 secs:     248 bytes ==> POST http://localhost:8081/orders   
HTTP/1.1 500     1.24 secs:     248 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 500     1.23 secs:     248 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 500     1.42 secs:     248 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 500     2.08 secs:     248 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     1.29 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 500     1.24 secs:     248 bytes ==> POST http://localhost:8081/orders

* 요청을 어느정도 돌려보내고나니, 기존에 밀린 일들이 처리되었고, 회로를 닫아 요청을 다시 받기 시작

HTTP/1.1 201     1.46 secs:     207 bytes ==> POST http://localhost:8081/orders  
HTTP/1.1 201     1.33 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     1.36 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     1.63 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     1.65 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     1.68 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     1.69 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     1.71 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     1.71 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     1.74 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     1.76 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     1.79 secs:     207 bytes ==> POST http://localhost:8081/orders

* 다시 요청이 쌓이기 시작하여 건당 처리시간이 610 밀리를 살짝 넘기기 시작 => 회로 열기 => 요청 실패처리

HTTP/1.1 500     1.93 secs:     248 bytes ==> POST http://localhost:8081/orders    
HTTP/1.1 500     1.92 secs:     248 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 500     1.93 secs:     248 bytes ==> POST http://localhost:8081/orders

* 생각보다 빨리 상태 호전됨 - (건당 (쓰레드당) 처리시간이 610 밀리 미만으로 회복) => 요청 수락

HTTP/1.1 201     2.24 secs:     207 bytes ==> POST http://localhost:8081/orders  
HTTP/1.1 201     2.32 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     2.16 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     2.19 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     2.19 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     2.19 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     2.21 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     2.29 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     2.30 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     2.38 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     2.59 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     2.61 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     2.62 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     2.64 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     4.01 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     4.27 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     4.33 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     4.45 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     4.52 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     4.57 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     4.69 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     4.70 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     4.69 secs:     207 bytes ==> POST http://localhost:8081/orders

* 이후 이러한 패턴이 계속 반복되면서 시스템은 도미노 현상이나 자원 소모의 폭주 없이 잘 운영됨


HTTP/1.1 500     4.76 secs:     248 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 500     4.23 secs:     248 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     4.76 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     4.74 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 500     4.82 secs:     248 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     4.82 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     4.84 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     4.66 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 500     5.03 secs:     248 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 500     4.22 secs:     248 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 500     4.19 secs:     248 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 500     4.18 secs:     248 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     4.69 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     4.65 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     5.13 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 500     4.84 secs:     248 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 500     4.25 secs:     248 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 500     4.25 secs:     248 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     4.80 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 500     4.87 secs:     248 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 500     4.33 secs:     248 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     4.86 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 500     4.96 secs:     248 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 500     4.34 secs:     248 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 500     4.04 secs:     248 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     4.50 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     4.95 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     4.54 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     4.65 secs:     207 bytes ==> POST http://localhost:8081/orders


:
:

Transactions:		        1025 hits
Availability:		       63.55 %
Elapsed time:		       59.78 secs
Data transferred:	        0.34 MB
Response time:		        5.60 secs
Transaction rate:	       17.15 trans/sec
Throughput:		        0.01 MB/sec
Concurrency:		       96.02
Successful transactions:        1025
Failed transactions:	         588
Longest transaction:	        9.20
Shortest transaction:	        0.00

```
- 운영시스템은 죽지 않고 지속적으로 CB 에 의하여 적절히 회로가 열림과 닫힘이 벌어지면서 자원을 보호하고 있음을 보여줌. 하지만, 63.55% 가 성공하였고, 46%가 실패했다는 것은 고객 사용성에 있어 좋지 않기 때문에 Retry 설정과 동적 Scale out (replica의 자동적 추가,HPA) 을 통하여 시스템을 확장 해주는 후속처리가 필요.

- Retry 의 설정 (istio)
- Availability 가 높아진 것을 확인 (siege)

### 오토스케일 아웃
앞서 CB 는 시스템을 안정되게 운영할 수 있게 해줬지만 사용자의 요청을 100% 받아들여주지 못했기 때문에 이에 대한 보완책으로 자동화된 확장 기능을 적용하고자 한다. 


- 결제서비스에 대한 replica 를 동적으로 늘려주도록 HPA 를 설정한다. 설정은 CPU 사용량이 15프로를 넘어서면 replica 를 10개까지 늘려준다:
```
kubectl autoscale deploy pay --min=1 --max=10 --cpu-percent=15
```
- CB 에서 했던 방식대로 워크로드를 2분 동안 걸어준다.
```
siege -c100 -t120S -r10 --content-type "application/json" 'http://localhost:8081/orders POST {"item": "chicken"}'
```
- 오토스케일이 어떻게 되고 있는지 모니터링을 걸어둔다:
```
kubectl get deploy pay -w
```
- 어느정도 시간이 흐른 후 (약 30초) 스케일 아웃이 벌어지는 것을 확인할 수 있다:
```
NAME    DESIRED   CURRENT   UP-TO-DATE   AVAILABLE   AGE
pay     1         1         1            1           17s
pay     1         2         1            1           45s
pay     1         4         1            1           1m
:
```
- siege 의 로그를 보아도 전체적인 성공률이 높아진 것을 확인 할 수 있다. 
```
Transactions:		        5078 hits
Availability:		       92.45 %
Elapsed time:		       120 secs
Data transferred:	        0.34 MB
Response time:		        5.60 secs
Transaction rate:	       17.15 trans/sec
Throughput:		        0.01 MB/sec
Concurrency:		       96.02
```


## Zero-Downtime deploy (Readiness Probe)
- 먼저 무정지 재배포가 100% 되는 것인지 확인하기 위해서 Autoscaler 이나 CB 설정을 제거하고 테스트함
- seige로 배포중에 부하를 발생과 재배포 실행
```bash
root@siege:/# siege -c1 -t30S -v http://resort:8080/resorts 
kubectl apply -f  kubernetes/deployment.yml 
```
- seige 의 화면으로 넘어가서 Availability 가 100% 미만으로 떨어졌는지 확인

<img width="552" alt="image" src="https://user-images.githubusercontent.com/85722851/125045082-922dd600-e0d7-11eb-9128-4c9eff39654c.png">
배포기간중 Availability 가 평소 100%에서 80% 대로 떨어지는 것을 확인. 원인은 쿠버네티스가 성급하게 새로 올려진 서비스를 READY 상태로 인식하여 서비스 유입을 진행한 것이기 때문. 

- 이를 막기위해 Readiness Probe 를 설정함: deployment.yaml 의 readiness probe 추가
```yml
          readinessProbe:
            httpGet:
              path: '/actuator/health'
              port: 8080
            initialDelaySeconds: 10
            timeoutSeconds: 2
            periodSeconds: 5
            failureThreshold: 10
```

- 동일한 시나리오로 재배포 한 후 Availability 확인
<img width="503" alt="image" src="https://user-images.githubusercontent.com/85722851/125044747-3cf1c480-e0d7-11eb-9c35-1091547bb099.png">
배포기간 동안 Availability 가 100%를 유지하기 때문에 무정지 재배포가 성공한 것으로 확인됨.

## Self-healing (Liveness Probe)
- Pod는 정상적으로 작동하지만 내부의 어플리케이션이 반응이 없다면, 컨테이너는 의미가 없다.
- 위와 같은 경우는 어플리케이션의 Liveness probe는 Pod의 상태를 체크하다가, Pod의 상태가 비정상인 경우 kubelet을 통해서 재시작한다.
- 임의대로 Liveness probe에서 path를 잘못된 값으로 변경 후, retry 시도 확인
```yml
          livenessProbe:
            httpGet:
              path: '/actuator/fakehealth' <-- path를 잘못된 값으로 변경
              port: 8080
            initialDelaySeconds: 120
            timeoutSeconds: 2
            periodSeconds: 5
            failureThreshold: 5
```
- resort Pod가 여러차례 재시작 한것을 확인할 수 있다.
<img width="757" alt="image" src="https://user-images.githubusercontent.com/85722851/125048777-3cf3c380-e0db-11eb-99cd-97c7ebead85f.png">


# 체크포인트

- 분석 설계


  - 이벤트스토밍: 
    - 스티커 색상별 객체의 의미를 제대로 이해하여 헥사고날 아키텍처와의 연계 설계에 적절히 반영하고 있는가?
    - 각 도메인 이벤트가 의미있는 수준으로 정의되었는가?
    - 어그리게잇: Command와 Event 들을 ACID 트랜잭션 단위의 Aggregate 로 제대로 묶었는가?
    - 기능적 요구사항과 비기능적 요구사항을 누락 없이 반영하였는가?    

  - 서브 도메인, 바운디드 컨텍스트 분리
    - 팀별 KPI 와 관심사, 상이한 배포주기 등에 따른  Sub-domain 이나 Bounded Context 를 적절히 분리하였고 그 분리 기준의 합리성이 충분히 설명되는가?
      - 적어도 3개 이상 서비스 분리
    - 폴리글랏 설계: 각 마이크로 서비스들의 구현 목표와 기능 특성에 따른 각자의 기술 Stack 과 저장소 구조를 다양하게 채택하여 설계하였는가?
    - 서비스 시나리오 중 ACID 트랜잭션이 크리티컬한 Use 케이스에 대하여 무리하게 서비스가 과다하게 조밀히 분리되지 않았는가?
  - 컨텍스트 매핑 / 이벤트 드리븐 아키텍처 
    - 업무 중요성과  도메인간 서열을 구분할 수 있는가? (Core, Supporting, General Domain)
    - Request-Response 방식과 이벤트 드리븐 방식을 구분하여 설계할 수 있는가?
    - 장애격리: 서포팅 서비스를 제거 하여도 기존 서비스에 영향이 없도록 설계하였는가?
    - 신규 서비스를 추가 하였을때 기존 서비스의 데이터베이스에 영향이 없도록 설계(열려있는 아키택처)할 수 있는가?
    - 이벤트와 폴리시를 연결하기 위한 Correlation-key 연결을 제대로 설계하였는가?

  - 헥사고날 아키텍처
    - 설계 결과에 따른 헥사고날 아키텍처 다이어그램을 제대로 그렸는가?
    
- 구현
  - [DDD] 분석단계에서의 스티커별 색상과 헥사고날 아키텍처에 따라 구현체가 매핑되게 개발되었는가?
    - Entity Pattern 과 Repository Pattern 을 적용하여 JPA 를 통하여 데이터 접근 어댑터를 개발하였는가
    - [헥사고날 아키텍처] REST Inbound adaptor 이외에 gRPC 등의 Inbound Adaptor 를 추가함에 있어서 도메인 모델의 손상을 주지 않고 새로운 프로토콜에 기존 구현체를 적응시킬 수 있는가?
    - 분석단계에서의 유비쿼터스 랭귀지 (업무현장에서 쓰는 용어) 를 사용하여 소스코드가 서술되었는가?
  - Request-Response 방식의 서비스 중심 아키텍처 구현
    - 마이크로 서비스간 Request-Response 호출에 있어 대상 서비스를 어떠한 방식으로 찾아서 호출 하였는가? (Service Discovery, REST, FeignClient)
    - 서킷브레이커를 통하여  장애를 격리시킬 수 있는가?
  - 이벤트 드리븐 아키텍처의 구현
    - 카프카를 이용하여 PubSub 으로 하나 이상의 서비스가 연동되었는가?
    - Correlation-key:  각 이벤트 건 (메시지)가 어떠한 폴리시를 처리할때 어떤 건에 연결된 처리건인지를 구별하기 위한 Correlation-key 연결을 제대로 구현 하였는가?
    - Message Consumer 마이크로서비스가 장애상황에서 수신받지 못했던 기존 이벤트들을 다시 수신받아 처리하는가?
    - Scaling-out: Message Consumer 마이크로서비스의 Replica 를 추가했을때 중복없이 이벤트를 수신할 수 있는가
    - CQRS: Materialized View 를 구현하여, 타 마이크로서비스의 데이터 원본에 접근없이(Composite 서비스나 조인SQL 등 없이) 도 내 서비스의 화면 구성과 잦은 조회가 가능한가?

  - 폴리글랏 플로그래밍
    - 각 마이크로 서비스들이 하나이상의 각자의 기술 Stack 으로 구성되었는가?
    - 각 마이크로 서비스들이 각자의 저장소 구조를 자율적으로 채택하고 각자의 저장소 유형 (RDB, NoSQL, File System 등)을 선택하여 구현하였는가?
  - API 게이트웨이
    - API GW를 통하여 마이크로 서비스들의 집입점을 통일할 수 있는가?
    - 게이트웨이와 인증서버(OAuth), JWT 토큰 인증을 통하여 마이크로서비스들을 보호할 수 있는가?
- 운영
  - SLA 준수
    - 셀프힐링: Liveness Probe 를 통하여 어떠한 서비스의 health 상태가 지속적으로 저하됨에 따라 어떠한 임계치에서 pod 가 재생되는 것을 증명할 수 있는가?
    - 서킷브레이커, 레이트리밋 등을 통한 장애격리와 성능효율을 높힐 수 있는가?
    - 오토스케일러 (HPA) 를 설정하여 확장적 운영이 가능한가?
    - 모니터링, 앨럿팅: 
  - 무정지 운영 CI/CD (10)
    - Readiness Probe 의 설정과 Rolling update을 통하여 신규 버전이 완전히 서비스를 받을 수 있는 상태일때 신규버전의 서비스로 전환됨을 siege 등으로 증명 
    - Contract Test :  자동화된 경계 테스트를 통하여 구현 오류나 API 계약위반를 미리 차단 가능한가?
