package com.docgen.dto;

/**
 * DTO for system resource usage (JVM, DB pool, Redis).
 */
public class SystemResourceDTO {

    private JvmMemory jvmMemory;
    private DbPool dbPool;
    private RedisMemory redisMemory;

    public SystemResourceDTO() {}

    public JvmMemory getJvmMemory() { return jvmMemory; }
    public void setJvmMemory(JvmMemory jvmMemory) { this.jvmMemory = jvmMemory; }

    public DbPool getDbPool() { return dbPool; }
    public void setDbPool(DbPool dbPool) { this.dbPool = dbPool; }

    public RedisMemory getRedisMemory() { return redisMemory; }
    public void setRedisMemory(RedisMemory redisMemory) { this.redisMemory = redisMemory; }

    /**
     * JVM heap memory usage.
     */
    public static class JvmMemory {
        private long usedBytes;
        private long maxBytes;
        private double usagePercent;

        public JvmMemory() {}

        public JvmMemory(long usedBytes, long maxBytes, double usagePercent) {
            this.usedBytes = usedBytes;
            this.maxBytes = maxBytes;
            this.usagePercent = usagePercent;
        }

        public long getUsedBytes() { return usedBytes; }
        public void setUsedBytes(long usedBytes) { this.usedBytes = usedBytes; }

        public long getMaxBytes() { return maxBytes; }
        public void setMaxBytes(long maxBytes) { this.maxBytes = maxBytes; }

        public double getUsagePercent() { return usagePercent; }
        public void setUsagePercent(double usagePercent) { this.usagePercent = usagePercent; }
    }

    /**
     * HikariCP database connection pool stats.
     */
    public static class DbPool {
        private int activeConnections;
        private int idleConnections;
        private int totalConnections;
        private int maxConnections;
        private double usagePercent;

        public DbPool() {}

        public int getActiveConnections() { return activeConnections; }
        public void setActiveConnections(int activeConnections) { this.activeConnections = activeConnections; }

        public int getIdleConnections() { return idleConnections; }
        public void setIdleConnections(int idleConnections) { this.idleConnections = idleConnections; }

        public int getTotalConnections() { return totalConnections; }
        public void setTotalConnections(int totalConnections) { this.totalConnections = totalConnections; }

        public int getMaxConnections() { return maxConnections; }
        public void setMaxConnections(int maxConnections) { this.maxConnections = maxConnections; }

        public double getUsagePercent() { return usagePercent; }
        public void setUsagePercent(double usagePercent) { this.usagePercent = usagePercent; }
    }

    /**
     * Redis memory usage info.
     */
    public static class RedisMemory {
        private long usedMemoryBytes;
        private long maxMemoryBytes;
        private double usagePercent;

        public RedisMemory() {}

        public RedisMemory(long usedMemoryBytes, long maxMemoryBytes, double usagePercent) {
            this.usedMemoryBytes = usedMemoryBytes;
            this.maxMemoryBytes = maxMemoryBytes;
            this.usagePercent = usagePercent;
        }

        public long getUsedMemoryBytes() { return usedMemoryBytes; }
        public void setUsedMemoryBytes(long usedMemoryBytes) { this.usedMemoryBytes = usedMemoryBytes; }

        public long getMaxMemoryBytes() { return maxMemoryBytes; }
        public void setMaxMemoryBytes(long maxMemoryBytes) { this.maxMemoryBytes = maxMemoryBytes; }

        public double getUsagePercent() { return usagePercent; }
        public void setUsagePercent(double usagePercent) { this.usagePercent = usagePercent; }
    }
}
