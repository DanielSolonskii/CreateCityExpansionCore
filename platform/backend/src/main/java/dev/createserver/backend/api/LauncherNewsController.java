package dev.createserver.backend.api;

import dev.createserver.backend.broadcast.LauncherNews;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/launcher/news")
public class LauncherNewsController {
    private final LauncherNews news;
    public LauncherNewsController(LauncherNews news) { this.news = news; }
    @GetMapping public List<LauncherNews.Item> list() { return news.list(); }
}
