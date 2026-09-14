package com.hmdp.controller.api;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.annotation.RateLimit;
import com.hmdp.annotation.RateLimitType;
import com.hmdp.dto.BlogCommentRequest;
import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.entity.BlogComments;
import com.hmdp.service.IBlogCommentsService;
import com.hmdp.service.IBlogService;
import com.hmdp.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/blogs")
public class ApiBlogController {
    @Resource private IBlogService blogService;
    @Resource private IBlogCommentsService commentsService;

    @GetMapping("/hot")
    public Result hot(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.queryHotBlog(current);
    }

    @GetMapping("/{id}")
    public Result detail(@PathVariable Long id) { return blogService.queryBlogById(id); }

    @PostMapping("/{id}/like")
    @RateLimit(key = "api:blog:like", limit = 20, windowSeconds = 60, type = RateLimitType.USER)
    public Result like(@PathVariable Long id) { return blogService.likeBlog(id); }

    @GetMapping("/{id}/comments")
    public Result comments(@PathVariable Long id) {
        List<BlogComments> comments = commentsService.list(new QueryWrapper<BlogComments>()
                .eq("blog_id", id).eq("status", 0).orderByDesc("create_time"));
        return Result.ok(comments);
    }

    @PostMapping("/{id}/comments")
    @RateLimit(key = "api:blog:comment", limit = 10, windowSeconds = 60, type = RateLimitType.USER)
    public Result comment(@PathVariable Long id, @Valid @RequestBody BlogCommentRequest request) {
        if (blogService.getById(id) == null) return Result.fail("笔记不存在");
        BlogComments comment = new BlogComments()
                .setUserId(UserHolder.getUser().getId()).setBlogId(id)
                .setParentId(request.getParentId() == null ? 0L : request.getParentId())
                .setAnswerId(request.getAnswerId() == null ? 0L : request.getAnswerId())
                .setContent(request.getContent().trim()).setLiked(0).setStatus(false);
        commentsService.save(comment);
        blogService.update().setSql("comments = comments + 1").eq("id", id).update();
        return Result.ok(comment.getId());
    }

    @GetMapping("/feed")
    public Result feed(@RequestParam("lastId") Long lastId,
                       @RequestParam(value = "offset", defaultValue = "0") Integer offset) {
        return blogService.queryBlogOfFollow(lastId, offset);
    }
}
