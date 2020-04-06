package uk.org.sehicl.testutilserver;

import java.io.IOException;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentSkipListMap;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonFactoryBuilder;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sendgrid.helpers.mail.Mail;

import redis.embedded.RedisServer;
import uk.org.sehicl.website.users.SessionData;
import uk.org.sehicl.website.users.User;
import uk.org.sehicl.website.users.User.Status;
import uk.org.sehicl.website.users.UserDatastore;
import uk.org.sehicl.website.users.impl.RedisDatastore;

@RestController
public class Controller
{
    @Autowired
    private RedisServer redisServer;

    private UserDatastore userDatastore;

    private final SortedMap<LocalDateTime, Mail> mails = new ConcurrentSkipListMap<>();

    @RequestMapping(path = "/recaptcha", produces = "application/json")
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

    @RequestMapping(path = "/v3/mail/send", method = RequestMethod.POST, produces = "application/json")
    public String sendMail(HttpServletRequest req) throws IOException
    {
        ObjectMapper mapper = new ObjectMapper();
        Mail mail = mapper.readValue(req.getInputStream(), Mail.class);
        mails.put(LocalDateTime.now(), mail);
        return mapper.writeValueAsString(mail);
    }

    @RequestMapping(path = "/lastMailTime")
    public String lastMailTime(HttpServletRequest req) throws IOException
    {
        LocalDateTime answer;
        try
        {
            answer = mails.lastKey();
        }
        catch (NoSuchElementException ex)
        {
            answer = LocalDateTime.of(0, 1, 1, 0, 0);
        }
        return answer.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    @RequestMapping(path = "/mailsSince/{dateTime}", produces = "application/json")
    public String mailsSince(HttpServletRequest req, @PathVariable String dateTime)
            throws IOException
    {
        LocalDateTime dt = LocalDateTime.parse(dateTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        ObjectMapper mapper = new ObjectMapper();
        StringWriter sw = new StringWriter();
        try (JsonGenerator generator = new JsonFactoryBuilder().build().createGenerator(sw))
        {
            generator.writeStartArray();
            for (Entry<LocalDateTime, Mail> e : mails
                    .tailMap(dt.plus(1, ChronoUnit.MICROS))
                    .entrySet())
            {
                generator.writeStartObject();
                generator
                        .writeStringField("dateTime",
                                e.getKey().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                generator.writeStringField("mail", mapper.writeValueAsString(e.getValue()));
                generator.writeEndObject();
            }
            generator.writeEndArray();
        }
        return sw.toString();
    }

    @RequestMapping(path = "/clearUsers")
    public String clearUsers(HttpServletRequest req) throws IOException
    {
        userDatastore.getAllUserIds().stream().forEach(userDatastore::deleteUser);
        return "";
    }

    @RequestMapping(path = "/createUser", method = RequestMethod.POST)
    public String createUser(HttpServletRequest req) throws IOException
    {
        User user = userDatastore
                .createUser(req.getParameter("email"), req.getParameter("name"), null,
                        req.getParameter("active") != null ? Status.ACTIVE : Status.INACTIVE,
                        req.getParameter("password"));
        String role = req.getParameter("role");
        if (role != null)
        {
            user.getRoles().add(role);
            userDatastore.updateUser(user);
        }
        return String.format("%d", user.getId());
    }

    @RequestMapping(path = "/queryUser/{email}")
    public String queryUser(HttpServletRequest req, HttpServletResponse resp,
            @PathVariable String email) throws IOException
    {
        User user = userDatastore.getUserByEmail(email);
        if (user == null)
        {
            resp.setStatus(HttpStatus.NOT_FOUND.value());
            return "";
        }
        SessionData session = userDatastore.getSessionByUserId(user.getId());
        StringWriter sw = new StringWriter();
        try (JsonGenerator generator = new JsonFactoryBuilder().build().createGenerator(sw))
        {
            generator.writeStartObject();
            generator.writeNumberField("id", user.getId());
            generator.writeStringField("status", user.getStatus().toString());
            if (session != null)
                generator.writeNumberField("session", session.getId());
            generator.writeEndObject();
        }
        return sw.toString();
    }

    @PostConstruct
    public void start()
    {
        try
        {
            redisServer.start();
        }
        catch (Throwable ex)
        {
        }
        userDatastore = new RedisDatastore(
                String.format("redis://%s:%d", "localhost", Application.REDIS_PORT));
    }

    @PreDestroy
    public void stop()
    {
        redisServer.stop();
    }
}