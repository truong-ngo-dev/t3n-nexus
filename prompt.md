Thêm Kafka và Kafka Connect (Debezium) vào docker-compose.yml sau. Không thay đổi các service đã có,
chỉ bổ sung.

docker-compose hiện tại: (paste nội dung file vào đây)

Yêu cầu:

1. Kafka — single broker, KRaft mode (không dùng Zookeeper), image apache/kafka:3.9.0

2. postgres-notification — cần thêm command để bật logical replication:
   command: postgres -c wal_level=logical

3. Kafka Connect — image debezium/connect:3.0, depends on Kafka và postgres-notification

Connector cần register qua REST API sau khi Connect healthy. Cung cấp connector config JSON riêng    
(không inline vào docker-compose) với các thông số:
- Monitor đúng 1 bảng: notification_log trong database notification_db
- Áp dụng 2 SMT theo thứ tự:
    - ExtractNewRecordState — flatten envelope Debezium, consumer nhận plain row thay vì {before,      
      after, op}
    - ContentBasedRouter — route dựa trên giá trị column:
        - channel=EMAIL AND tier=TRANSACTIONAL → topic notification.email.transactional
        - channel=EMAIL AND tier=BULK → topic notification.email.bulk
        - channel=IN_APP → topic notification.inapp.dispatch

4. Tất cả service mới phải có healthcheck

5. Cập nhật volumes section nếu có volume mới
