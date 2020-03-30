package uk.org.sehicl.testutilserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import redis.embedded.RedisServer;

@SpringBootApplication
public class Application
{
    public static final int REDIS_PORT = 6389;

    public static void main(String[] args)
    {
        try
        {
            SpringApplication.run(Application.class, args);
        }
        catch (Throwable ex)
        {
            ex.printStackTrace();
        }
    }

    @Bean
    public RedisServer redisServer()
    {
        return new RedisServer(REDIS_PORT);
    }
}
