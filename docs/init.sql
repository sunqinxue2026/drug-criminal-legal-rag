-- =============================================
-- 法律咨询智能问答系统 - 完整数据库初始化脚本
-- =============================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS legal_rag DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE legal_rag;

-- =============================================
-- 1. 用户表
-- =============================================
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` varchar(50) NOT NULL COMMENT '用户名',
  `password` varchar(255) NOT NULL COMMENT '密码',
  `real_name` varchar(50) DEFAULT NULL COMMENT '真实姓名',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `avatar` varchar(255) DEFAULT NULL COMMENT '头像URL',
  `role` varchar(20) NOT NULL DEFAULT 'user' COMMENT '角色：admin-管理员，user-普通用户',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '删除标记：0-未删除，1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 插入测试用户
-- 管理员：admin / admin123
-- 普通用户：user1 / 123456, user2 / 123456
INSERT INTO `user` (`username`, `password`, `real_name`, `role`, `status`) VALUES
('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '系统管理员', 'admin', 1),
('user1', '$2a$10$EblZqNptyYvcLm/VwDCVAuBjzZOI7khzdyGPBr08PpIi0na624b8.', '张三', 'user', 1),
('user2', '$2a$10$EblZqNptyYvcLm/VwDCVAuBjzZOI7khzdyGPBr08PpIi0na624b8.', '李四', 'user', 1);

-- =============================================
-- 2. 法律领域分类表
-- =============================================
DROP TABLE IF EXISTS `category`;
CREATE TABLE `category` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '分类ID',
  `name` varchar(100) NOT NULL COMMENT '分类名称',
  `description` varchar(255) DEFAULT NULL COMMENT '分类描述',
  `parent_id` bigint NOT NULL DEFAULT 0 COMMENT '父分类ID',
  `sort` int NOT NULL DEFAULT 0 COMMENT '排序',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='法律领域分类表';

-- 插入法律领域分类
INSERT INTO `category` (`name`, `description`, `sort`) VALUES
('民事法律', '民事相关法律案例', 1),
('刑事法律', '刑事相关法律案例', 2),
('行政法律', '行政相关法律案例', 3),
('经济法律', '经济相关法律案例', 4),
('劳动法律', '劳动相关法律案例', 5),
('婚姻家庭', '婚姻家庭相关法律案例', 6),
('房产法律', '房产相关法律案例', 7),
('交通法律', '交通相关法律案例', 8),
('知识产权', '知识产权相关法律案例', 9),
('合同法律', '合同相关法律案例', 10);

-- =============================================
-- 3. 法律案例表（完整版）
-- =============================================
DROP TABLE IF EXISTS `item`;
CREATE TABLE `item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '案例ID',
  `title` varchar(255) NOT NULL COMMENT '案例标题',
  `content` text NOT NULL COMMENT '案例内容',
  `summary` varchar(500) DEFAULT NULL COMMENT '摘要/裁判要点',
  `category_id` bigint DEFAULT NULL COMMENT '分类ID',
  `category` varchar(100) DEFAULT NULL COMMENT '法律领域名称',
  `court` varchar(200) DEFAULT NULL COMMENT '法院',
  `case_number` varchar(100) DEFAULT NULL COMMENT '案号',
  `judgment_date` date DEFAULT NULL COMMENT '裁判日期',
  `legal_basis` text COMMENT '法律依据',
  `plaintiff` varchar(200) DEFAULT NULL COMMENT '原告',
  `defendant` varchar(200) DEFAULT NULL COMMENT '被告',
  `case_type` varchar(50) DEFAULT NULL COMMENT '案件类型',
  `trial_procedure` varchar(50) DEFAULT NULL COMMENT '审理程序',
  `tags` varchar(255) DEFAULT NULL COMMENT '标签',
  `view_count` int NOT NULL DEFAULT 0 COMMENT '浏览次数',
  `like_count` int NOT NULL DEFAULT 0 COMMENT '点赞次数',
  `favorite_count` int NOT NULL DEFAULT 0 COMMENT '收藏次数',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0-草稿，1-已发布',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_category_id` (`category_id`),
  KEY `idx_category` (`category`),
  KEY `idx_title` (`title`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='法律案例表';

-- 插入8条完整的法律案例数据（与截图一致）
INSERT INTO `item` (`title`, `content`, `summary`, `category_id`, `category`, `court`, `case_number`, `judgment_date`, `legal_basis`, `plaintiff`, `defendant`, `case_type`, `trial_procedure`, `tags`, `view_count`, `status`) VALUES
('醉驾+肇事逃逸案', 
'被告人在醉酒状态下驾驶机动车发生交通事故后逃逸，造成一人轻伤。经鉴定，被告人血液中酒精含量为180mg/100ml，属于醉酒驾驶。事故发生后，被告人明知发生交通事故仍驾车逃离现场。', 
'醉酒驾驶,肇事逃逸,从重处罚', 
2, '刑事辩护', '某区人民法院', '(2023)京0105刑初123号', '2023-08-14', 
'《中华人民共和国刑法》第133条、第133条之一', 
'人民检察院', '张某', '危险驾驶罪', '一审', 
'刑事辩护,危险驾驶罪,交通肇事', 156, 1),

('虚假出资收取保证金诈骗案', 
'被告人以虚假出资为名，收取被害人保证金/履约金，收取后占为己有，拒不返还。被告人通过伪造公司资质、虚构投资项目等方式，骗取多名被害人信任，累计诈骗金额达50余万元。', 
'虚假出资,保证金诈骗,诈骗罪', 
2, '刑事辩护', '某区人民法院', '(2023)京0106刑初234号', '2023-08-14', 
'《中华人民共和国刑法》第266条', 
'人民检察院', '李某', '诈骗罪', '一审', 
'刑事辩护,诈骗罪,合同诈骗', 142, 1),

('股权转让纠纷处理', 
'股东之间因股权转让价格和转让条件产生争议。原告主张按照公司章程约定的价格转让股权，被告则认为应当按照市场价格评估。双方就股权转让价格、支付方式、交割时间等条款无法达成一致。', 
'股权转让,公平原则,保护权益', 
4, '商事争议', '某中级人民法院', '(2023)京02民初345号', '2023-08-14', 
'《中华人民共和国公司法》第71条', 
'王某', '赵某', '股权转让纠纷', '一审', 
'商事争议,股权转让,公司法', 128, 1),

('劳动争议仲裁案', 
'员工因公司违法解除劳动合同要求赔偿。公司以员工严重违反规章制度为由解除劳动合同，但未能提供充分证据证明员工存在严重违纪行为。员工要求公司支付违法解除劳动合同赔偿金。', 
'违法解除,劳动合同,赔偿金', 
5, '劳动争议', '某区劳动仲裁委', '(2023)京劳仲字第456号', '2023-08-14', 
'《中华人民共和国劳动合同法》第87条', 
'刘某', '某科技公司', '劳动合同纠纷', '仲裁', 
'劳动争议,劳动合同,赔偿', 115, 1),

('离婚财产分割案', 
'夫妻双方因财产分割和子女抚养权产生争议。双方婚后共同购买房产一套，现因感情破裂协议离婚，但就房产归属、存款分割、子女抚养权及抚养费等问题无法达成一致意见。', 
'夫妻共同财产,平等分割,子女抚养权', 
6, '民事纠纷', '某区人民法院', '(2023)京0107民初567号', '2023-08-14', 
'《中华人民共和国民法典》婚姻家庭编', 
'陈某', '周某', '离婚纠纷', '一审', 
'民事纠纷,离婚,财产分割', 203, 1),

('商品房买卖合同纠纷', 
'购房者因开发商延期交房要求赔偿违约金。开发商承诺2023年6月30日前交房，但实际交房时间延迟至2023年12月，延期交房达6个月。购房者要求开发商按照合同约定支付违约金。', 
'延期交房,违约金,合同约定', 
7, '房产纠纷', '某区人民法院', '(2023)京0108民初678号', '2023-08-14', 
'《中华人民共和国民法典》合同编', 
'吴某', '某房地产公司', '房屋买卖合同纠纷', '一审', 
'房产纠纷,商品房,违约', 187, 1),

('交通事故人身损害赔偿', 
'机动车交通事故造成受害人人身损害，要求赔偿。被告驾驶机动车与原告发生碰撞，造成原告多处骨折，住院治疗3个月。原告要求被告赔偿医疗费、误工费、护理费、精神损害抚慰金等各项损失。', 
'交通事故,人身损害,赔偿责任', 
8, '交通事故', '某区人民法院', '(2023)京0109民初789号', '2023-08-14', 
'《中华人民共和国民法典》侵权责任编', 
'郑某', '孙某', '机动车交通事故责任纠纷', '一审', 
'交通事故,人身损害,赔偿', 165, 1),

('知识产权侵权纠纷', 
'被告未经许可使用原告的注册商标，构成侵权。被告在其生产销售的商品上使用了与原告注册商标相同的标识，侵犯了原告的商标专用权。原告要求被告停止侵权行为并赔偿经济损失。', 
'商标侵权,未经许可,赔偿责任', 
9, '知识产权', '某中级人民法院', '(2023)京03民初890号', '2023-08-14', 
'《中华人民共和国商标法》第57条', 
'某科技公司', '某贸易公司', '商标权侵权纠纷', '一审', 
'知识产权,商标侵权,赔偿', 145, 1);

-- =============================================
-- 4. 聊天会话表
-- =============================================
DROP TABLE IF EXISTS `chat_session`;
CREATE TABLE `chat_session` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '会话ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `title` varchar(255) DEFAULT '新对话' COMMENT '会话标题',
  `last_message` text COMMENT '最后一条消息',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天会话表';

-- =============================================
-- 5. 聊天消息表
-- =============================================
DROP TABLE IF EXISTS `chat_message`;
CREATE TABLE `chat_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '消息ID',
  `session_id` bigint NOT NULL COMMENT '会话ID',
  `role` varchar(20) NOT NULL COMMENT '角色：user-用户，assistant-助手',
  `content` text NOT NULL COMMENT '消息内容',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_session_id` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天消息表';

-- =============================================
-- 6. 收藏表
-- =============================================
DROP TABLE IF EXISTS `favorite`;
CREATE TABLE `favorite` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '收藏ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `item_id` bigint NOT NULL COMMENT '案例ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_item` (`user_id`, `item_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_item_id` (`item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收藏表';

-- =============================================
-- 7. 点赞表
-- =============================================
DROP TABLE IF EXISTS `item_like`;
CREATE TABLE `item_like` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '点赞ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `item_id` bigint NOT NULL COMMENT '案例ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_item` (`user_id`, `item_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_item_id` (`item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='点赞表';

-- =============================================
-- 8. 评论表
-- =============================================
DROP TABLE IF EXISTS `comment`;
CREATE TABLE `comment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '评论ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `item_id` bigint NOT NULL COMMENT '案例ID',
  `content` text NOT NULL COMMENT '评论内容',
  `parent_id` bigint NOT NULL DEFAULT 0 COMMENT '父评论ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_item_id` (`item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评论表';

-- =============================================
-- 9. 用户行为记录表
-- =============================================
DROP TABLE IF EXISTS `user_action`;
CREATE TABLE `user_action` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '行为ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `username` varchar(50) NOT NULL COMMENT '用户名',
  `action_type` varchar(50) NOT NULL COMMENT '行为类型：view-浏览，search-搜索，chat-咨询，favorite-收藏，login-登录',
  `action_target` varchar(255) DEFAULT NULL COMMENT '操作对象',
  `action_detail` text COMMENT '详细信息',
  `ip_address` varchar(50) DEFAULT NULL COMMENT 'IP地址',
  `user_agent` varchar(500) DEFAULT NULL COMMENT '用户代理',
  `target_id` bigint DEFAULT NULL COMMENT '目标ID',
  `target_type` varchar(50) DEFAULT NULL COMMENT '目标类型',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_action_type` (`action_type`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户行为记录表';

-- 插入用户行为记录示例
INSERT INTO `user_action` (`user_id`, `username`, `action_type`, `action_target`, `action_detail`, `ip_address`, `user_agent`) VALUES
(2, 'user1', 'view', '醉驾+肇事逃逸案', '浏览案例详情', '192.168.1.100', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'),
(2, 'user1', 'search', '劳动合同', '搜索关键词：劳动合同', '192.168.1.100', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'),
(2, 'user1', 'chat', '劳动合同纠纷咨询', '咨询劳动合同相关问题', '192.168.1.100', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'),
(2, 'user1', 'favorite', '劳动争议仲裁案', '收藏案例', '192.168.1.100', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'),
(2, 'user1', 'view', '离婚财产分割案', '浏览案例详情', '192.168.1.100', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)');

-- =============================================
-- 验证数据
-- =============================================
SELECT '========== 数据库初始化完成 ==========' AS '';
SELECT CONCAT('用户数量: ', COUNT(*)) AS '' FROM user;
SELECT CONCAT('分类数量: ', COUNT(*)) AS '' FROM category;
SELECT CONCAT('案例数量: ', COUNT(*)) AS '' FROM item;
SELECT CONCAT('行为记录: ', COUNT(*)) AS '' FROM user_action;
SELECT '======================================' AS '';
SELECT '✅ 数据库初始化成功！' AS message;
SELECT '📝 管理员账号: admin / admin123' AS '';
SELECT '📝 测试账号: user1 / 123456' AS '';
