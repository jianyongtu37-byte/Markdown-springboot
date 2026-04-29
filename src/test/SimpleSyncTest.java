import java.sql.*;

public class SimpleSyncTest {
    
    private static final String DB_URL_3306 = "jdbc:mysql://localhost:3306/markdown_db?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true";
    private static final String DB_URL_3307 = "jdbc:mysql://localhost:3307/markdown_db?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "root";
    
    public static void main(String[] args) {
        System.out.println("=== MySQL数据检查（端口3306和3307） ===");
        
        // 检查两个端口的数据库
        checkDatabase("端口3306（本地MySQL）", DB_URL_3306);
        checkDatabase("端口3307（Docker MySQL）", DB_URL_3307);
    }
    
    private static void checkDatabase(String label, String dbUrl) {
        System.out.println("\n--- " + label + " ---");
        
        try {
            // 1. 连接到MySQL
            Connection conn = DriverManager.getConnection(dbUrl, DB_USER, DB_PASSWORD);
            System.out.println("成功连接到MySQL数据库");
            
            // 2. 检查所有表的数据
            checkTableData(conn, "article", "文章");
            checkTableData(conn, "sys_user", "用户");
            checkTableData(conn, "category", "分类");
            checkTableData(conn, "tag", "标签");
            checkTableData(conn, "article_tag", "文章标签关联");
            
            // 3. 检查article表的详细数据
            System.out.println("\n=== 文章详细统计 ===");
            String[] statsQueries = {
                "SELECT COUNT(*) as total FROM article",
                "SELECT COUNT(*) as published FROM article WHERE status = 1 AND is_public = 1",
                "SELECT COUNT(*) as draft FROM article WHERE status = 0",
                "SELECT COUNT(*) as private FROM article WHERE status = 1 AND is_public = 0"
            };
            
            String[] statLabels = {"文章总数", "已发布且公开", "草稿", "已发布但私密"};
            
            Statement stmt = conn.createStatement();
            for (int i = 0; i < statsQueries.length; i++) {
                ResultSet rs = stmt.executeQuery(statsQueries[i]);
                if (rs.next()) {
                    System.out.println(statLabels[i] + ": " + rs.getInt(1));
                }
                rs.close();
            }
            
            // 4. 如果有文章数据，显示前5篇
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) as cnt FROM article");
            int articleCount = 0;
            if (rs.next()) {
                articleCount = rs.getInt("cnt");
            }
            rs.close();
            
            if (articleCount > 0) {
                System.out.println("\n=== 前5篇文章 ===");
                rs = stmt.executeQuery("SELECT id, title, status, is_public, user_id, create_time FROM article ORDER BY id LIMIT 5");
                int rowCount = 0;
                while (rs.next()) {
                    rowCount++;
                    Long id = rs.getLong("id");
                    String title = rs.getString("title");
                    int status = rs.getInt("status");
                    int isPublic = rs.getInt("is_public");
                    Long userId = rs.getLong("user_id");
                    Timestamp createTime = rs.getTimestamp("create_time");
                    
                    System.out.println(rowCount + ". ID: " + id + 
                                     ", 标题: " + title + 
                                     ", 状态: " + (status == 1 ? "已发布" : "草稿") +
                                     ", 公开: " + (isPublic == 1 ? "是" : "否") +
                                     ", 用户ID: " + userId +
                                     ", 创建时间: " + createTime);
                }
                rs.close();
            } else {
                System.out.println("\n没有找到文章数据");
            }
            
            stmt.close();
            conn.close();
            
        } catch (SQLException e) {
            System.err.println("数据库连接失败: " + e.getMessage());
            System.err.println("可能原因:");
            if (dbUrl.contains("3306")) {
                System.err.println("1. 本地MySQL服务未运行");
                System.err.println("2. 没有安装MySQL");
            } else {
                System.err.println("1. Docker MySQL容器未运行");
                System.err.println("2. 数据库markdown_db不存在");
            }
            System.err.println("3. 用户名或密码错误");
        } catch (Exception e) {
            System.err.println("发生错误: " + e.getMessage());
        }
    }
    
    private static void checkTableData(Connection conn, String tableName, String label) throws SQLException {
        try {
            Statement stmt = conn.createStatement();
            String sql = "SELECT COUNT(*) as cnt FROM " + tableName;
            ResultSet rs = stmt.executeQuery(sql);
            
            if (rs.next()) {
                int count = rs.getInt("cnt");
                System.out.println(label + "表(" + tableName + ")记录数: " + count);
            }
            
            rs.close();
            stmt.close();
        } catch (SQLException e) {
            System.err.println("检查" + label + "表(" + tableName + ")失败: " + e.getMessage());
        }
    }
}