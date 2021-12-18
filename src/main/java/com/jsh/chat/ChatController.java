package com.jsh.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;

@RequiredArgsConstructor
@RestController
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
    @PostMapping("/chat")
    public Mono<Chat> setMsg(@RequestBody Chat chat){
        chat.setCreatedAt(LocalDateTime.now());
        return chatRepository.save(chat);
    }// 한 건 리턴
}
