package com.example.userservice.service;

import com.example.userservice.client.OrderSerivceClient;
import com.example.userservice.dto.UserDto;
import com.example.userservice.jpa.UserEntity;
import com.example.userservice.jpa.UserRepository;
import com.example.userservice.vo.ResponseOrder;
import feign.FeignException;
import feign.Response;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.modelmapper.spi.MatchingStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class UserServiceImpl implements UserService {

    UserRepository userRepository;
    BCryptPasswordEncoder passwordEncoder;

    Environment env;
    RestTemplate restTemplate;

    OrderSerivceClient orderSerivceClient;

    CircuitBreakerFactory circuitBreakerFactory;


    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserEntity userEntity = userRepository.findByEmail(username);

        if(userEntity == null)
            throw new UsernameNotFoundException(username);

        return new User(userEntity.getEmail(),userEntity.getEncryptedPwd(),
            true,true,true,true,new ArrayList<>());
    }

    @Autowired
    public UserServiceImpl(UserRepository userRepository,BCryptPasswordEncoder passwordEncoder,
                            Environment env,RestTemplate restTemplate,OrderSerivceClient orderSerivceClient,CircuitBreakerFactory circuitBreakerFactory){
        this.userRepository = userRepository;
        this.passwordEncoder= passwordEncoder;
        this.env = env;
        this.restTemplate = restTemplate;
        this.orderSerivceClient= orderSerivceClient;
        this.circuitBreakerFactory = circuitBreakerFactory;
    }

    @Override
    public UserDto createUser(UserDto userDto) {
        userDto.setUserId(UUID.randomUUID().toString());

        ModelMapper mapper = new ModelMapper();
        mapper.getConfiguration().setMatchingStrategy(MatchingStrategies.STRICT);
        UserEntity userEntity = mapper.map(userDto, UserEntity.class);
        userEntity.setEncryptedPwd(passwordEncoder.encode(userDto.getPwd()));
        userRepository.save(userEntity);

        UserDto userDto1 = mapper.map(userEntity,UserDto.class);
        return userDto1;
    }

    @Override
    public UserDto getUserByUserId(String userId) {
        UserEntity userEntity = userRepository.findByUserId(userId);

        if(userEntity == null) throw new UsernameNotFoundException("User not fouund");

        UserDto userDto = new ModelMapper().map(userEntity,UserDto.class);

        //List<ResponseOrder> orders = new ArrayList<>();
        /*/
         Using as rest template
         */
//        String orderUrl =  String.format(env.getProperty("order_service.url"),userId);
//        ResponseEntity<List<ResponseOrder>> ordersListResponse =
//            restTemplate.exchange(orderUrl, HttpMethod.GET, null,
//                    new ParameterizedTypeReference<List<ResponseOrder>>() { // 호출하려는 오더서비스의 메소드의 반환값 적용.
//                    });
//        List<ResponseOrder> orderList = ordersListResponse.getBody();

        /**
         * Using a feign client
         */
        List<ResponseOrder> orderList = null;
//        try{
//            orderList =orderSerivceClient.getOrders(userId);
//
//        }catch (FeignException ex){
//            log.error(ex.getMessage());
//        }

        /* Error Decoder*/
       // orderList =orderSerivceClient.getOrders(userId);
        log.info("Before call orders microservice.");
        CircuitBreaker circuitBreaker = circuitBreakerFactory.create("circuitbreaker");
        orderList = circuitBreaker.run(()-> orderSerivceClient.getOrders(userId), throwable -> new ArrayList<>());

        log.info("After call orders microservice.");

        userDto.setResponseOrders(orderList);

        return userDto;
    }

    @Override
    public Iterable<UserEntity> getUserByAll() {
        return userRepository.findAll();
    }

    @Override
    public UserDto getUserDetailsByEmail(String email) {
        UserEntity userEntity =  userRepository.findByEmail(email);
        UserDto userDto = new ModelMapper().map(userEntity,UserDto.class);

        if(userEntity == null)
            throw new UsernameNotFoundException(email);

        return userDto;
    }
}
