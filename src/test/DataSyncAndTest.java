import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nineone.markdown.entity.Article;
import com.nineone.markdown.entity.User;
import com.nineone.markdown.entity.Category;
import com.nineone.markdown.mapper.ArticleMapper;
import com.nineone.markdown.mapper.UserMapper;
import com.nineone.markdown.mapper.CategoryMapper;
import com.nineone.markdown.service.SearchService;
import com.nineone.markdown.service.ArticleService;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@SpringBootApplication(webEnvironment = SpringBootApplication.WebEnvironment.NONE)
@EnableElasticsearchRepositories(basePackages = "com.nineone.markdown.repository.es")
@EnableTransactionManagement
public class DataSyncAndTest {
    
    @Component
    static class DataInitializer implements CommandLineRunner {
        
        private final UserMapper userMapper;
        private final CategoryMapper categoryMapper;
        private final ArticleMapper articleMapper;
        private final ArticleService articleService;
        private final SearchService searchService;
        
        DataInitializer(UserMapper userMapper, CategoryMapper categoryMapper, 
                       ArticleMapper articleMapper, ArticleService articleService,
                       SearchService searchService) {
            this.userMapper = userMapper;
            this.categoryMapper = categoryMapper;
            this.articleMapper = articleMapper;
            this.articleService = articleService;
            this.searchService = searchService;
        }
        
        @Override
        public void run(String... args) throws Exception {
            System.out.println("=== 开始数据初始化和同步测试 ===");
            
            // 1. 检查并创建测试用户
            User testUser = ensureTestUserExists();
            
            // 2. 检查并创建测试分类
            Category testCategory = ensureTestCategoryExists();
            
            // 3. 创建测试文章（如果不存在）
            createTestArticles(testUser, testCategory);
            
            // 4. 同步数据到Elasticsearch
            syncToElasticsearch();
            
            // 5. 测试搜索功能
            testSearchFunctionality();
            
            System.out.println("=== 数据初始化和同步测试完成 ===");
        }
        
        private User ensureTestUserExists() {
            LambdaQueryWrapper<User> query = new LambdaQueryWrapper<>();
            query.eq(User::getUsername, "testuser");
            User user = userMapper.selectOne(query);
            
            if (user == null) {
                user = User.builder()
                    .username("testuser")
                    .nickname("测试用户")
                    .email("test@example.com")
                    .password("$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7eiK.Ez0e") // 123456
                    .status(1)
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build();
                userMapper.insert(user);
                System.out.println("创建测试用户: ID=" + user.getId());
            } else {
                System.out.println("使用现有测试用户: ID=" + user.getId());
            }
            return user;
        }
        
        private Category ensureTestCategoryExists() {
            LambdaQueryWrapper<Category> query = new LambdaQueryWrapper<>();
            query.eq(Category::getName, "测试分类");
            Category category = categoryMapper.selectOne(query);
            
            if (category == null) {
                category = Category.builder()
                    .name("测试分类")
                    .description("用于测试的分类")
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build();
                categoryMapper.insert(category);
                System.out.println("创建测试分类: ID=" + category.getId());
            } else {
                System.out.println("使用现有测试分类: ID=" + category.getId());
            }
            return category;
        }
        
        private void createTestArticles(User user, Category category) {
            // 检查是否已有文章
            LambdaQueryWrapper<Article> query = new LambdaQueryWrapper<>();
            query.eq(Article::getUserId, user.getId());
            long articleCount = articleMapper.selectCount(query);
            
            if (articleCount == 0) {
                System.out.println("创建测试文章...");
                
                // 文章1：关于Spring Boot的测试文章
                Article article1 = Article.builder()
                    .userId(user.getId())
                    .categoryId(category.getId())
                    .title("Spring Boot入门教程")
                    .content("Spring Boot是一个用于创建独立的、生产级的Spring应用程序的框架。它简化了Spring应用的初始搭建和开发过程。本教程将介绍Spring Boot的基本概念和使用方法。")
                    .summary("Spring Boot入门指南")
                    .aiStatus(2)
                    .status(1) // 已发布
                    .isPublic(1) // 公开
                    .viewCount(10)
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build();
                articleMapper.insert(article1);
                System.out.println("创建文章1: " + article1.getTitle() + " (ID=" + article1.getId() + ")");
                
                // 文章2：关于Elasticsearch的测试文章
                Article article2 = Article.builder()
                    .userId(user.getId())
                    .categoryId(category.getId())
                    .title("Elasticsearch中文搜索实践")
                    .content("Elasticsearch是一个基于Lucene的分布式搜索和分析引擎。它支持全文搜索、结构化搜索和数据分析。本文介绍如何在Elasticsearch中实现中文搜索功能。")
                    .summary("Elasticsearch中文搜索配置指南")
                    .aiStatus(2)
                    .status(1) // 已发布
                    .isPublic(1) // 公开
                    .viewCount(25)
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build();
                articleMapper.insert(article2);
                System.out.println("创建文章2: " + article2.getTitle() + " (ID=" + article2.getId() + ")");
                
                // 文章3：关于MySQL的测试文章
                Article article3 = Article.builder()
                    .userId(user.getId())
                    .categoryId(category.getId())
                    .title("MySQL数据库优化技巧")
                    .content("MySQL是一个流行的关系型数据库管理系统。性能优化是数据库管理的重要部分。本文分享一些MySQL数据库性能优化的实用技巧。")
                    .summary("MySQL性能优化指南")
                    .aiStatus(2)
                    .status(1) // 已发布
                    .isPublic(1) // 公开
                    .viewCount(15)
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build();
                articleMapper.insert(article3);
                System.out.println("创建文章3: " + article3.getTitle() + " (ID=" + article3.getId() + ")");
            } else {
                System.out.println("已有 " + articleCount + " 篇文章存在，跳过创建");
            }
        }
        
        private void syncToElasticsearch() {
            System.out.println("\n=== 同步数据到Elasticsearch ===");
            
            // 检查当前ES中的文档数量
            long esCount = searchService.getIndexStats();
            System.out.println("当前Elasticsearch中的文档数量: " + esCount);
            
            // 重建所有索引
            System.out.println("开始重建所有索引...");
            int syncedCount = searchService.rebuildAllIndexes();
            System.out.println("成功同步 " + syncedCount + " 篇文章到Elasticsearch");
            
            // 再次检查ES中的文档数量
            esCount = searchService.getIndexStats();
            System.out.println("同步后Elasticsearch中的文档数量: " + esCount);
        }
        
        private void testSearchFunctionality() {
            System.out.println("\n=== 测试搜索功能 ===");
            
            long esCount = searchService.getIndexStats();
            if (esCount > 0) {
                // 测试搜索"Spring"
                System.out.println("1. 搜索关键词: Spring");
                try {
                    var results = searchService.searchArticles("Spring", 1, 10);
                    System.out.println("  搜索到 " + results.size() + " 条结果");
                    if (!results.isEmpty()) {
                        results.forEach(result -> 
                            System.out.println("  - ID: " + result.getId() + 
                                             ", 标题: " + result.getTitle() + 
                                             ", 得分: " + result.getScore()));
                    }
                } catch (Exception e) {
                    System.out.println("  搜索失败: " + e.getMessage());
                }
                
                // 测试搜索"Elasticsearch"
                System.out.println("\n2. 搜索关键词: Elasticsearch");
                try {
                    var results = searchService.searchArticles("Elasticsearch", 1, 10);
                    System.out.println("  搜索到 " + results.size() + " 条结果");
                    if (!results.isEmpty()) {
                        results.forEach(result -> 
                            System.out.println("  - ID: " + result.getId() + 
                                             ", 标题: " + result.getTitle() + 
                                             ", 得分: " + result.getScore()));
                    }
                } catch (Exception e) {
                    System.out.println("  搜索失败: " + e.getMessage());
                }
                
                // 测试搜索"数据库"
                System.out.println("\n3. 搜索关键词: 数据库");
                try {
                    var results = searchService.searchArticles("数据库", 1, 10);
                    System.out.println("  搜索到 " + results.size() + " 条结果");
                    if (!results.isEmpty()) {
                        results.forEach(result -> 
                            System.out.println("  - ID: " + result.getId() + 
                                             ", 标题: " + result.getTitle() + 
                                             ", 得分: " + result.getScore()));
                    }
                } catch (Exception e) {
                    System.out.println("  搜索失败: " + e.getMessage());
                }
                
                // 测试搜索"教程"
                System.out.println("\n4. 搜索关键词: 教程");
                try {
                    var results = searchService.searchArticles("教程", 1, 10);
                    System.out.println("  搜索到 " + results.size() + " 条结果");
                    if (!results.isEmpty()) {
                        results.forEach(result -> 
                            System.out.println("  - ID: " + result.getId() + 
                                             ", 标题: " + result.getTitle() + 
                                             ", 得分: " + result.getScore()));
                    }
                } catch (Exception e) {
                    System.out.println("  搜索失败: " + e.getMessage());
                }
            } else {
                System.out.println("警告: Elasticsearch中没有数据，无法测试搜索功能");
            }
        }
    }
    
    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(DataSyncAndTest.class, args);
        
        // 程序会自动运行CommandLineRunner
        // 完成后等待几秒然后关闭
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        context.close();
    }
}