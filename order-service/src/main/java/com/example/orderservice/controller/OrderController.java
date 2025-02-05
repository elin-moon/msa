package com.example.orderservice.controller;

import com.example.orderservice.dto.OrderDto;
import com.example.orderservice.jpa.OrderEntity;
import com.example.orderservice.messagequeue.KafkaProducer;
import com.example.orderservice.messagequeue.OrderProducer;
import com.example.orderservice.service.OrderService;
import com.example.orderservice.vo.RequestOrder;
import com.example.orderservice.vo.ResponseOrder;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.errors.InterruptException;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/order-service")
@Slf4j
public class OrderController {
    Environment env;
    OrderService orderService ;
    KafkaProducer kafkaProducer;
    OrderProducer orderProducer;

    @Autowired
    public OrderController(Environment env, OrderService orderService,KafkaProducer kafkaProducer,OrderProducer orderProducer){
        this.env=env;
        this.orderService= orderService;
        this.kafkaProducer=kafkaProducer;
        this.orderProducer=orderProducer;
    }

    @PostMapping("/{userId}/orders")
    public ResponseEntity<ResponseOrder> createOrder(@PathVariable String userId, @RequestBody RequestOrder order){
        log.info("Before add orders data.");
        ModelMapper mapper = new ModelMapper();
        mapper.getConfiguration().setMatchingStrategy(MatchingStrategies.STRICT);

        OrderDto orderDto = mapper.map(order,OrderDto.class);
        orderDto.setUserId(userId);
        OrderDto createdOrder = orderService.createOrder(orderDto);
        ResponseOrder responseOrder = mapper.map(createdOrder,ResponseOrder.class);

        /*kafka */
       // orderDto.setOrderId(UUID.randomUUID().toString());
       // orderDto.setTotalPrice(order.getQty()*order.getUnitPrice());


        /* send this order to the kafka */
        kafkaProducer.send("example-catalog-topic",orderDto);
      //  orderProducer.send("orders2",orderDto);

       // ResponseOrder responseOrder = mapper.map(orderDto,ResponseOrder.class);
        log.info("After add orders data.");
        return ResponseEntity.status(HttpStatus.CREATED).body(responseOrder);
    }

    @GetMapping("/{userId}/orders")
    public ResponseEntity<List<ResponseOrder>> getOrder(@PathVariable String userId) throws Exception{
        log.info("Before retrieve orders data.");
        Iterable<OrderEntity> orderList =  orderService.getOrdersByUserId(userId);

        List<ResponseOrder> list= new ArrayList<>();
        orderList.forEach(v->{
            list.add(new ModelMapper().map(v, ResponseOrder.class));
        });


//        try{
//            Thread.sleep(1000);
//            throw new Exception("장애 발생");
//        }catch (InterruptedException e){
//            log.error(e.getMessage());
//        }

        log.info("After retrieve orders data.");
        return ResponseEntity.status(HttpStatus.OK).body(list);
    }
}

