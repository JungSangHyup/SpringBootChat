# SpringBootChat

![image-20220105100423771](https://user-images.githubusercontent.com/51068026/148144960-7619acb3-e7dc-4794-b582-8186229a3e0f.png)

 * [개요](#개요)
 * [구현 기술](#구현-기술)
   + [Netty 서버](#Netty-서버)
   + [몽고 DB](#몽고-DB)
   + [SSE 프로토콜](#SSE-프로토콜)
 * [구현](#구현)
   + [챗 페이지](#챗-페이지)
   + [테스트](#테스트)
 * [마무리](#마무리)

## 개요

SpringBoot (Netty) + MongoDB 로 만드는 Chat

## 구현 기술

Netty랑 몽고 db의 선정된 이유에 대해 알아보자

### Netty 서버 

Netty는 유지하기 쉬운 높은 성능의 프로토콜 서버 및 클라이언트를 신속한 개발을 위한 **비동기 이벤트 드리븐 네트워크 어플리케이션 프레임워크**입니다.

> Netty is an asynchronous event-driven network application framework for rapid development of maintainable high performance protocol servers & clients.
>
> 출처: [Netty Project](http://netty.io/)

Netty 는 프로토콜 서버 및 클라이언트 같은 네트워크 어플리케이션의 빠르고 쉬운 개발을 가능하게 하는 [NIO](http://en.wikipedia.org/wiki/Non-blocking_I/O_(Java)) 클라이언트 서버 프레임워크입니다. 이는 TCP 또는 UDP 소켓 서버와 같은 네트워크 프로그래밍을 간단하게 할 수 있습니다.

> Netty is a NIO client server framework which enables quick and easy development of network applications such as protocol servers and clients. It greatly simplifies and streamlines network programming such as TCP and UDP socket server.
>
> 출처: [Netty Project](http://netty.io/)

 여기서 주목해야 할 건 비동기 이벤트 기반의 프레임워크 라는 것이다. 이것을 통해 따로 쓰레드를 만들지 않고 비동기 형식으로 데이터를 주고 받을 수 있다. 

### 몽고 DB

>  **몽고DB**(MongoDB←HUMONGOUS)는 크로스 플랫폼 [도큐먼트 지향 데이터베이스](https://ko.wikipedia.org/w/index.php?title=도큐먼트_지향_데이터베이스&action=edit&redlink=1) 시스템이다. [NoSQL](https://ko.wikipedia.org/wiki/NoSQL) 데이터베이스로 분류되는 몽고DB는 [JSON](https://ko.wikipedia.org/wiki/JSON)과 같은 동적 스키마형 도큐먼트들(몽고DB는 이러한 포맷을 [BSON](https://ko.wikipedia.org/w/index.php?title=BSON&action=edit&redlink=1)이라 부름)을 선호함에 따라 전통적인 테이블 기반 [관계형 데이터베이스](https://ko.wikipedia.org/wiki/관계형_데이터베이스) 구조의 사용을 삼간다.
>
>  출처:  [위키피디아](https://ko.wikipedia.org/wiki/%EB%AA%BD%EA%B3%A0DB)

몽고 DB는 document 기반의 데이터베이스라는 것이다. 

**대표적인 특징은 데이터 접근이 빠르지만 수정은 어렵다는 것이다.**

그러나 채팅창의 지나간 대화 메시지를 수정하는 일은 없으므로 빠른 접근이 가능한 몽고 DB가 채팅에는 더욱 적합하다. 

### SSE 프로토콜

> SSE를 사용하여 통신 할 때 서버는 초기 요청을 하지 않고도 필요할 때마다 데이터를 앱으로 푸시 할 수 있습니다. 즉, 서버에서 클라이언트로 업데이트를 스트리밍 할 수 있습니다. SSE는 서버와 클라이언트 사이에 단일 단방향 채널을 엽니다.
>
> 출처:  [HAMA 블로그](https://hamait.tistory.com/792)

 채팅을 이벤트 스트림으로 처리할 경우 서버로 부터 계속 정보를 들고와서 뿌려줘야한다. SSE는 서버와 클라이언트 사이에 단방향으로 데이터를 계속 내려받을 수 있다.

## 구현

1. 필요한 라이브러리를 추가해준다.

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-mongodb-reactive</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```

2. 몽고 db chat collection을 생성해주고 설정파일에 연결한다.

```yaml
spring:
    data:
        mongodb:
            host: localhost
            port: 27017
            database: chatdb
```

3. 몽고 db 모델링을 구축한다

```java
@Data
@Document(collection = "chat")
public class Chat {
    @Id
    private String id;
    private String msg;
    private String sender; // 보내는 사람
    private String receiver; // 받는 사람(귓속말)
    private int roomNum; // 방 번호

    private LocalDateTime createdAt;
}
```

4. Chat Repository 를 설정한다.

```java
public interface ChatRepository extends ReactiveMongoRepository<Chat, String> {
    @Tailable
    @Query("{sender:?0, receiver:?1}")
    Flux<Chat> mFindBySender(String sender, String receiver);
    
    @Tailable
    @Query("{ roomNum: ?0 }")
    Flux<Chat> mFindByRoomNum(int roomNum);
}
```

- @Tailable : 커서를 안 닫고 계속 유지한다.

- Flux(흐름) : 계속해서 받겠다 response를 유지하면서 데이터 흘려보내기

5. 간단한 컨트롤러를 작성한다.

```java
public class ChatController {
    @Autowired
    private final ChatRepository chatRepository;

    @CrossOrigin
    @GetMapping(value = "/sender/{sender}/receiver/{receiver}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Chat> getMsg(@PathVariable String sender, @PathVariable String receiver){
        return chatRepository.mFindBySender(sender, receiver)
                .subscribeOn(Schedulers.boundedElastic());
    }// 여러 건 리턴

    @CrossOrigin
    @GetMapping(value = "/chat/roomNum/{roomNum}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public Flux<Chat> findByRoomNum(@PathVariable int roomNum){
        return chatRepository.mFindByRoomNum(roomNum)
                .subscribeOn(Schedulers.boundedElastic());
    }// 여러 건 리턴

    @CrossOrigin
    @PostMapping("/chat")
    public Mono<Chat> setMsg(@RequestBody Chat chat){
        chat.setCreatedAt(LocalDateTime.now());
        return chatRepository.save(chat);
    }// 한 건 리턴
}
```

### 챗 페이지

1. eventSource 구현

```javascript
const eventSource = new EventSource(`http://localhost:8080/chat/roomNum/${roomNum}`);

eventSource.onmessage = (event) => {
    const data = JSON.parse(event.data);
    if(data.sender == username){
        initMyMessage(data);
    }else{
        initYourMessage(data);
    }
}
```

방 넘버에 있는 메시지를 받아서 화면에 구현하는 코드이다.

[EventSource에 대해 더 알아보기](https://developer.mozilla.org/ko/docs/Web/API/EventSource)

2. 내가 보내는 메시지

```javascript
function addMessage(){
    let msgInput = document.querySelector("#chat-outgoing-msg");

    let chat = {
        sender: username,
        roomNum: roomNum,
        msg: msgInput.value
    };

    fetch("http://localhost:8080/chat", {
        method: "post",
        body: JSON.stringify(chat), // JS -> JSON
        headers: {
            "Content-Type":"application/json; charset=utf-8"
        }
    });

    msgInput.value = "";
}
```

### 테스트

1. 라이브 서버로 해당 페이지를 열어보면 아이디 입력창과 방번호가 뜬다.

![image-20220104100928852](https://user-images.githubusercontent.com/51068026/147996966-8081f360-cafb-4745-97f0-5a6ee411a055.png)
![image-20220104101019429](https://user-images.githubusercontent.com/51068026/147996971-508804b6-302e-490c-bc1f-9e25d6e1abff.png)


2.  페이지 창 두 개를 만들어 서로 대화를 해본다

![image-20220104101142019](https://user-images.githubusercontent.com/51068026/147997009-5bc49332-f048-4693-b320-852b844e2638.png)

3. '/chat/roomNum/{roomNum}' 에서 대화가 되면 mongo db에 업데이트 되는걸 확인할 수 있다.

![image-20220104101356765](https://user-images.githubusercontent.com/51068026/147997016-c0ecf91a-9001-4ffb-b0dd-8b9154d7917f.png)

## 마무리

