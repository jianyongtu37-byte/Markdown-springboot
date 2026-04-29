import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableElasticsearchRepositories(basePackages = "com.nineone.markdown.repository.es")
public class EsDataSyncTest {
    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(EsDataSyncTest.class, args);
        
        try {
            System.out.println("=== Elasticsearch 数据同步测试开始 ===");
            
            // 获取SearchService Bean
            com.nineone.markdown.service.SearchService searchService = 
                context.getBean(com.nineone.markdown.service.SearchService.class);
            
            // 获取ArticleMapper Bean
            com.nineone.markdown.mapper.ArticleMapper articleMapper = 
                context.getBean(com.nineone.markdown.mapper.ArticleMapper.class);
            
            // 1. 检查当前ES中的文档数量
            long esCount = searchService.getIndexStats();
            System.out.println("当前Elasticsearch中的文档数量: " + esCount);
            
            // 2. 获取MySQL中的文章数量
            int mysqlCount = articleMapper.selectCount(null);
            System.out.println("MySQL中的文章总数: " + mysqlCount);
            
            // 3. 重建所有索引
            System.out.println("开始重建所有索引...");
            int syncedCount = searchService.rebuildAllIndexes();
            System.out.println("成功同步 " + syncedCount + " 篇文章到Elasticsearch");
            
            // 4. 再次检查ES中的文档数量
            esCount = searchService.getIndexStats();
            System.out.println("同步后Elasticsearch中的文档数量: " + esCount);
            
            // 5. 测试搜索功能
            System.out.println("\n=== 测试搜索功能 ===");
            if (esCount > 0) {
                System.out.println("尝试搜索测试关键词...");
                try {
                    var results = searchService.searchArticles("测试", 1, 10);
                    System.out.println("搜索到 " + results.size() + " 条结果");
                    if (!results.isEmpty()) {
                        for (int i = 0; i < Math.min(results.size(), 3); i++) {
                            var result = results.get(i);
                            System.out.println((i+1) + ". ID: " + result.getId() + 
                                             ", 标题: " + result.getTitle() + 
                                             ", 得分: " + result.getScore());
                        }
                    }
                } catch (Exception e) {
                    System.out.println("搜索测试失败: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                System.out.println("警告: Elasticsearch中没有数据，无法测试搜索功能");
                System.out.println("可能原因: ");
                System.out.println("1. MySQL中没有文章数据");
                System.out.println("2. 所有文章都是草稿或非公开状态");
                System.out.println("3. 索引过程出现错误");
            }
            
            System.out.println("\n=== 数据同步完成 ===");
            
        } catch (Exception e) {
            System.err.println("数据同步过程中出现错误: ");
            e.printStackTrace();
        } finally {
            context.close();
        }
    }
}