package com.jsh.chat;

import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.data.mongodb.repository.Tailable;
import reactor.core.publisher.Flux;

public interface ChatRepository extends ReactiveMongoRepository<Chat, String> {
    @Tailable // 커서를 안 닫고 계속 유지한다.
    @Query("{sender:?0, receiver:?1}")// 몽고디비 문법ㅇ
    Flux<Chat> mFindBySender(String sender, String receiver);// Flux(흐름) 계속해서 받겠다 response를 유지하면서 데이터 흘려보내기
}
