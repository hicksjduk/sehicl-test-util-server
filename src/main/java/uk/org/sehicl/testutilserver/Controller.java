package uk.org.sehicl.testutilserver;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Random;
import java.util.Scanner;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonFactoryBuilder;
import com.fasterxml.jackson.core.JsonGenerator;

import redis.embedded.RedisServer;
import uk.org.sehicl.website.users.User;
import uk.org.sehicl.website.users.User.Status;
import uk.org.sehicl.website.users.UserDatastore;
import uk.org.sehicl.website.users.impl.RedisDatastore;

@RestController
public class Controller
{
    @Autowired
    RedisServer redisServer;

    UserDatastore userDatastore;

    User adminUser;

    @RequestMapping(path = "/recaptcha")
    public String approveRecaptcha(HttpServletRequest req) throws IOException
    {
        StringWriter sw = new StringWriter();
        try (JsonGenerator generator = new JsonFactoryBuilder().build().createGenerator(sw))
        {
            generator.writeStartObject();
            generator.writeBooleanField("success", true);
            generator.writeEndObject();
        }
        String answer = sw.toString();
        return answer;
    }

    @RequestMapping(path = "/adminCredentials")
    public String adminCredentials(HttpServletRequest req) throws IOException
    {
        StringWriter sw = new StringWriter();
        try (JsonGenerator generator = new JsonFactoryBuilder().build().createGenerator(sw))
        {
            generator.writeStartObject();
            generator.writeStringField("id", adminUser.getEmail());
            generator.writeStringField("pw", adminUser.getDecodedPassword());
            generator.writeEndObject();
        }
        String answer = sw.toString();
        return answer;
    }

    @RequestMapping(path = "/v3/mail/send", method = RequestMethod.POST)
    public String sendMail(HttpServletRequest req) throws IOException
    {
        String body;
        try (Scanner scanner = new Scanner(req.getInputStream()))
        {
            body = scanner.useDelimiter("\\A").next();
        }
        System.out.println(body);
        return "";
    }

    @PostConstruct
    public void start()
    {
        boolean startedNew = true;
        try
        {
            redisServer.start();
        }
        catch (Throwable ex)
        {
            startedNew = false;
        }
        userDatastore = new RedisDatastore(
                String.format("redis://%s:%d", "localhost", Application.REDIS_PORT));
        if (!startedNew)
        {
            userDatastore.getAllUserIds().stream().forEach(userDatastore::deleteUser);
        }
        adminUser = userDatastore
                .createUser("admin@sehicl.org.uk", "Admin", "", Status.ACTIVE, randomString(40));
    }

    @PreDestroy
    public void stop()
    {
        redisServer.stop();
    }

    private String randomString(int length)
    {
        int[] chars = Stream
                .of(IntStream.rangeClosed('a', 'z'), IntStream.rangeClosed('A', 'Z'),
                        IntStream.rangeClosed('0', '9'))
                .flatMapToInt(ch -> ch)
                .toArray();
        StringBuilder sb = new StringBuilder(length);
        Random rand = new Random();
        for (int i = 0; i < length; i++)
            sb.append((char) chars[rand.nextInt(chars.length)]);
        return sb.toString();
    }
}