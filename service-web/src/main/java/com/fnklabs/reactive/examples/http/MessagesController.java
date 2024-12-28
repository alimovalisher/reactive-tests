package com.fnklabs.reactive.examples.http;

import io.micrometer.core.annotation.Timed;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.bind.annotation.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/messages")
public class MessagesController {

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public MessagesController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public List<Message> getMessages(@RequestParam("author") String author) {
        return jdbcTemplate.query("select id, text,  author, created_at from message where author = ?", new RowMapper<Message>() {

            @Override
            public Message mapRow(ResultSet rs, int rowNum) throws SQLException {
                return new Message(
                        rs.getString("id"),
                        rs.getString("text"),
                        rs.getString("author"),
                        ZonedDateTime.ofInstant(rs.getTimestamp("created_at").toInstant(), ZoneOffset.UTC)
                );
            }
        }, author);
    }


    @PostMapping
    public ResponseEntity<Void> addMessages(@RequestBody List<Message> messages) {
        for (Message message : messages) {
            jdbcTemplate.update("insert into message (id, text, author, created_at) values (?,?,?,?)", message.id(), message.text(), message.author(), Date.from(message.createdAt().toInstant()));
        }

        return ResponseEntity.ok()
                             .build();
    }
}
