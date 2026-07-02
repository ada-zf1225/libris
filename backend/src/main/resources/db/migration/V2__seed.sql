-- Seed data.
-- Bibliographic records are real published works (real titles/authors/publishers/ISBNs).
-- User accounts are fictional demo identities; passwords are documented in the README.

-- ---------- policies ----------

INSERT INTO loan_policies (reader_type, loan_days, max_loans, max_renewals, daily_fine_cents, block_overdue_count, block_fine_cents)
VALUES ('TEACHER', 60, 20, 2, 10, 3, 1000),
       ('STUDENT', 30, 10, 1, 10, 3, 1000);

-- ---------- categories (Chinese Library Classification, 中图法) ----------

INSERT INTO categories (id, code, name_zh, name_en) VALUES
 (1,  'A', '马克思主义、列宁主义、毛泽东思想、邓小平理论', 'Marxism, Leninism, Maoism & Deng Xiaoping Theory'),
 (2,  'B', '哲学、宗教', 'Philosophy & Religion'),
 (3,  'C', '社会科学总论', 'Social Sciences (General)'),
 (4,  'D', '政治、法律', 'Politics & Law'),
 (5,  'E', '军事', 'Military Science'),
 (6,  'F', '经济', 'Economics'),
 (7,  'G', '文化、科学、教育、体育', 'Culture, Science, Education & Sports'),
 (8,  'H', '语言、文字', 'Language & Linguistics'),
 (9,  'I', '文学', 'Literature'),
 (10, 'J', '艺术', 'Art'),
 (11, 'K', '历史、地理', 'History & Geography'),
 (12, 'N', '自然科学总论', 'Natural Sciences (General)'),
 (13, 'O', '数理科学和化学', 'Mathematics, Physical Sciences & Chemistry'),
 (14, 'P', '天文学、地球科学', 'Astronomy & Earth Sciences'),
 (15, 'Q', '生物科学', 'Bioscience'),
 (16, 'R', '医药、卫生', 'Medicine & Health'),
 (17, 'S', '农业科学', 'Agriculture'),
 (18, 'T', '工业技术', 'Industrial Technology'),
 (19, 'U', '交通运输', 'Transportation'),
 (20, 'V', '航空、航天', 'Aviation & Aerospace'),
 (21, 'X', '环境科学、安全科学', 'Environmental & Safety Science'),
 (22, 'Z', '综合性图书', 'General Works');

-- ---------- users (fictional demo accounts) ----------

INSERT INTO users (username, password_hash, display_name, role, email, preferred_locale) VALUES
 ('admin', '$2y$10$QRv7HpBNWq08yh9iL4Qmf.MRTCWRb/0iEcd7M6DHsKETVY9WYN4mm', '系统管理员', 'ADMIN', 'admin@libris.local', 'zh-CN');

INSERT INTO users (username, password_hash, display_name, role, email, preferred_locale) VALUES
 ('zhanghua',     '$2y$10$phcewVRi9GOjNPOny9yYDeLl6yEAJbsqRuns8yb3eYxIJ2NNhwxiG', '张华',   'READER', 'zhanghua@libris.local', 'zh-CN'),
 ('wangxiaowei',  '$2y$10$phcewVRi9GOjNPOny9yYDeLl6yEAJbsqRuns8yb3eYxIJ2NNhwxiG', '王小伟', 'READER', 'wangxiaowei@libris.local', 'zh-CN'),
 ('wangwaner',    '$2y$10$phcewVRi9GOjNPOny9yYDeLl6yEAJbsqRuns8yb3eYxIJ2NNhwxiG', '王莞尔', 'READER', 'wangwaner@libris.local', 'en'),
 ('zhangminghua', '$2y$10$phcewVRi9GOjNPOny9yYDeLl6yEAJbsqRuns8yb3eYxIJ2NNhwxiG', '张明华', 'READER', 'zhangminghua@libris.local', 'zh-CN'),
 ('liyichen',     '$2y$10$phcewVRi9GOjNPOny9yYDeLl6yEAJbsqRuns8yb3eYxIJ2NNhwxiG', '李一琛', 'READER', 'liyichen@libris.local', 'zh-CN'),
 ('lierfei',      '$2y$10$phcewVRi9GOjNPOny9yYDeLl6yEAJbsqRuns8yb3eYxIJ2NNhwxiG', '李二飞', 'READER', 'lierfei@libris.local', 'zh-CN');

INSERT INTO reader_profiles (user_id, reader_type, sex, birth, address)
SELECT id, v.reader_type, v.sex, v.birth::date, v.address
FROM users u
JOIN (VALUES
 ('zhanghua',     'TEACHER', '男', '1985-06-10', '天津市'),
 ('wangxiaowei',  'TEACHER', '男', '1986-02-01', '北京市'),
 ('wangwaner',    'TEACHER', '女', '1985-04-15', '浙江省杭州市'),
 ('zhangminghua', 'STUDENT', '男', '2002-08-29', '陕西省西安市'),
 ('liyichen',     'STUDENT', '男', '2003-01-01', '陕西省西安市'),
 ('lierfei',      'STUDENT', '男', '2003-05-03', '山东省青岛市')
) AS v(username, reader_type, sex, birth, address) ON u.username = v.username;

-- ---------- books (real bibliographic records) ----------

INSERT INTO books (title, author, publisher, isbn, intro, language, price_cents, pub_date, category_id) VALUES
-- A 马列 / B 哲学
 ('共产党宣言', '[德] 马克思, 恩格斯', '人民出版社', '9787010083872', '科学社会主义的纲领性文献。', '中文', 1200, '2014-12-01', 1),
 ('苏菲的世界', '[挪] 乔斯坦·贾德', '作家出版社', '9787506394864', '以小说笔法讲述西方哲学史的入门经典。', '中文', 3800, '2017-01-01', 2),
 ('少有人走的路', '[美] M·斯科特·派克', '吉林文史出版社', '9787807023777', '关于自律、爱与心智成熟的心理学经典。', '中文', 2600, '2007-01-01', 2),
 ('追寻生命的意义', '[奥] 维克多·弗兰克', '新华出版社', '9787501162734', '意义疗法创始人对苦难与存在价值的思考。', '中文', 1200, '2003-01-01', 2),
-- C 社科 / D 政法 / E 军事
 ('乡土中国', '费孝通', '人民出版社', '9787010151441', '中国基层社会结构研究的社会学经典。', '中文', 2600, '2015-10-01', 3),
 ('枪炮、病菌与钢铁', '[美] 贾雷德·戴蒙德', '上海译文出版社', '9787532776627', '从地理与生态视角解释人类社会不平等的起源。', '中文', 6800, '2017-07-01', 3),
 ('民主的细节', '刘瑜', '上海三联书店', '9787542629845', '以案例观察当代美国政治运作的评论集。', '中文', 2900, '2009-06-01', 4),
 ('孙子兵法·孙膑兵法', '[春秋] 孙武, [战国] 孙膑', '中华书局', '9787101076080', '中国古代军事思想的奠基之作,全注全译本。', '中文', 2800, '2011-01-01', 5),
-- F 经济
 ('经济学原理(上下册)', '[美] N. 格里高利·曼昆', '机械工业出版社', '9787111126768', '全球广泛使用的经济学入门教材。', '中文', 8800, '2003-08-01', 6),
 ('薛兆丰经济学讲义', '薛兆丰', '中信出版社', '9787508693101', '面向大众的经济学思维讲义。', '中文', 6900, '2018-07-01', 6),
 ('原则', '[美] 瑞·达利欧', '中信出版社', '9787508684031', '桥水基金创始人的工作与生活原则。', '中文', 9800, '2018-01-01', 6),
-- G 文化教育
 ('娱乐至死', '[美] 尼尔·波兹曼', '中信出版社', '9787508648286', '论电视媒介如何重塑公共话语的传播学经典。', '中文', 3800, '2015-05-01', 7),
 ('乌合之众:大众心理研究', '[法] 古斯塔夫·勒庞', '中央编译出版社', '9787511704375', '群体心理学的开山之作。', '中文', 2980, '2011-05-01', 7),
-- H 语言
 ('现代汉语词典(第7版)', '中国社会科学院语言研究所词典编辑室', '商务印书馆', '9787100124508', '现代汉语规范词典,第 7 版。', '中文', 10900, '2016-09-01', 8),
 ('新华字典(第12版)', '中国社会科学院语言研究所', '商务印书馆', '9787100170932', '发行量最大的现代汉语字典,第 12 版。', '中文', 3290, '2020-07-01', 8),
-- I 文学
 ('三体', '刘慈欣', '重庆出版社', '9787536692930', '地球文明与三体文明的第一次接触,雨果奖获奖作品系列首部。', '中文', 2300, '2008-01-01', 9),
 ('三体Ⅱ·黑暗森林', '刘慈欣', '重庆出版社', '9787536693968', '面壁计划与宇宙社会学的黑暗森林法则。', '中文', 3200, '2008-05-01', 9),
 ('三体Ⅲ·死神永生', '刘慈欣', '重庆出版社', '9787229030933', '三体三部曲终章,程心与宇宙的终局。', '中文', 3800, '2010-11-01', 9),
 ('活着', '余华', '作家出版社', '9787506365437', '福贵一生的苦难与坚韧,当代中国文学代表作。', '中文', 2000, '2012-08-01', 9),
 ('百年孤独', '[哥伦比亚] 加西亚·马尔克斯', '南海出版公司', '9787544253994', '布恩迪亚家族七代人的魔幻现实主义史诗。', '中文', 3950, '2011-06-01', 9),
 ('红楼梦', '[清] 曹雪芹, 高鹗', '人民文学出版社', '9787020002207', '中国古典小说巅峰,程乙本校注版。', '中文', 5960, '1996-12-01', 9),
 ('围城', '钱锺书', '人民文学出版社', '9787020024759', '"城外的人想进去,城里的人想出来。"', '中文', 1900, '1991-02-01', 9),
 ('平凡的世界(全三册)', '路遥', '北京十月文艺出版社', '9787530216781', '黄土高原上普通人的奋斗史诗,茅盾文学奖作品。', '中文', 10800, '2017-06-01', 9),
 ('白夜行', '[日] 东野圭吾', '南海出版公司', '9787544258609', '横跨十九年的罪与爱,东野圭吾代表作。', '中文', 3950, '2013-01-01', 9),
 ('解忧杂货店', '[日] 东野圭吾', '南海出版公司', '9787544270878', '一家可以跨越时空通信的杂货店,治愈系长篇。', '中文', 3980, '2014-05-01', 9),
 ('大雪中的山庄', '[日] 东野圭吾', '北京十月文艺出版社', '9787530216835', '与外界隔绝的民宿里,七位演员的舞台剧变成生存悬案。', '中文', 3500, '2017-06-01', 9),
 ('造彩虹的人', '[日] 东野圭吾', '北京十月文艺出版社', '9787530216859', '三个迷惘的年轻人被一道彩虹般的光改变人生。', '中文', 3950, '2017-06-01', 9),
 ('控方证人', '[英] 阿加莎·克里斯蒂', '新星出版社', '9787513325745', '法庭推理短篇集,同名话剧六十年常演不衰。', '中文', 3500, '2017-05-01', 9),
 ('小王子', '[法] 圣埃克苏佩里', '人民文学出版社', '9787020042494', '写给大人的童话:关于驯养、玫瑰与星星。', '中文', 2200, '2003-08-01', 9),
 ('1984', '[英] 乔治·奥威尔', '北京十月文艺出版社', '9787530210291', '反乌托邦三部曲之一,"老大哥在看着你"。', '中文', 2800, '2010-04-01', 9),
 ('哈利·波特与魔法石', '[英] J.K. 罗琳', '人民文学出版社', '9787020033294', '哈利·波特系列首部,魔法世界的开端。', '中文', 1950, '2000-09-01', 9),
 ('挪威的森林', '[日] 村上春树', '上海译文出版社', '9787532725694', '青春、爱与丧失的成长小说。', '中文', 2300, '2001-02-01', 9),
 ('何以笙箫默', '顾漫', '朝华出版社', '9787505414709', '一段年少爱恋牵出的一生纠缠。', '中文', 1500, '2007-04-01', 9),
 ('三生三世 十里桃花', '唐七公子', '沈阳出版社', '9787544138000', '白浅与夜华三生三世的仙侠情缘。', '中文', 2680, '2009-01-01', 9),
 ('11处特工皇妃', '潇湘冬儿', '江苏文艺出版社', '9787539943893', '特工穿越题材长篇小说,全三册。', '中文', 7480, '2011-05-01', 9),
 ('方向', '[法] 马克-安托万·马修', '后浪 | 北京联合出版公司', '9787020125265', '不着一字的实验漫画,读者自行破解方向与意义。', '中文', 9980, '2017-04-01', 9),
 ('画的秘密', '[法] 马克-安托万·马修', '后浪 | 北京联合出版公司', '9787550265608', '结合镜像与 3D 叙事的悬疑图像小说,谢尔漫画节获奖作品。', '中文', 6000, '2016-01-01', 9),
-- J 艺术 / K 历史
 ('艺术的故事', '[英] 贡布里希', '广西美术出版社', '9787549408183', '西方艺术史最经典的入门读物。', '中文', 28000, '2014-01-01', 10),
 ('秘密花园', '[英] 乔汉娜·贝斯福', '北京联合出版公司', '9787550252585', '风靡全球的手绘涂色书。', '中文', 4200, '2015-06-01', 10),
 ('人类简史:从动物到上帝', '[以] 尤瓦尔·赫拉利', '中信出版社', '9787508647357', '从认知革命到科学革命,重述十万年人类史。', '中文', 6800, '2014-11-01', 11),
 ('未来简史:从智人到智神', '[以] 尤瓦尔·赫拉利', '中信出版社', '9787508672069', '数据主义时代人类将走向何方。', '中文', 6800, '2017-02-01', 11),
 ('万历十五年', '[美] 黄仁宇', '生活·读书·新知三联书店', '9787108009821', '以 1587 年为切片的大历史观察。', '中文', 1800, '1997-05-01', 11),
 ('全球通史:从史前史到21世纪(第7版 上册)', '[美] 斯塔夫里阿诺斯', '北京大学出版社', '9787301109489', '全球视野下的世界通史经典教材。', '中文', 4800, '2006-10-01', 11),
 ('明朝那些事儿(1-9)', '当年明月', '中国海关出版社', '9787801656087', '以小说笔法全景讲述明朝三百年。', '中文', 35820, '2009-04-01', 11),
-- N/O/P/Q 自然科学
 ('时间简史(插图本)', '[英] 史蒂芬·霍金', '湖南科学技术出版社', '9787535732309', '从大爆炸到黑洞的宇宙学科普经典。', '中文', 4500, '2002-01-01', 12),
 ('从一到无穷大', '[美] 乔治·伽莫夫', '科学出版社', '9787030029331', '横跨数学、物理与生物的科普名著。', '中文', 2900, '2002-11-01', 12),
 ('什么是数学:对思想和方法的基本研究', '[美] R. 柯朗, H. 罗宾', '复旦大学出版社', '9787309048285', '数学思想与方法的百科式导引。', '中文', 5500, '2005-05-01', 13),
 ('费马大定理:一个困惑了世间智者358年的谜', '[英] 西蒙·辛格', '上海译文出版社', '9787532736044', '一个数学猜想三个多世纪的证明之旅。', '中文', 3200, '2005-05-01', 13),
 ('暗淡蓝点', '[美] 卡尔·萨根', '人民邮电出版社', '9787115366016', '从太空回望地球,人类在宇宙中的位置。', '中文', 6900, '2014-11-01', 14),
 ('自私的基因', '[英] 理查德·道金斯', '中信出版社', '9787508634203', '以基因视角重新理解演化与利他行为。', '中文', 4900, '2012-09-01', 15),
-- R 医药
 ('众病之王:癌症传', '[美] 悉达多·穆克吉', '中信出版社', '9787508639826', '普利策奖作品,癌症的四千年编年史。', '中文', 4900, '2013-08-01', 16),
 ('最好的告别', '[美] 阿图·葛文德', '浙江人民出版社', '9787213066856', '关于衰老、死亡与医学边界的思考。', '中文', 3900, '2015-07-01', 16),
-- T 工业技术(含计算机)
 ('深入理解计算机系统(原书第3版)', '[美] Randal E. Bryant, David R. O''Hallaron', '机械工业出版社', '9787111544937', 'CSAPP:程序员视角下的计算机系统。', '中文', 13900, '2016-11-01', 18),
 ('算法导论(原书第3版)', '[美] Thomas H. Cormen 等', '机械工业出版社', '9787111407010', 'CLRS:算法领域的标准教材与参考书。', '中文', 12800, '2013-01-01', 18),
 ('Introduction to Algorithms, Fourth Edition', 'Thomas H. Cormen, Charles E. Leiserson, Ronald L. Rivest, Clifford Stein', 'MIT Press', '9780262046305', 'The standard reference on algorithms, fourth edition.', '英文', 14900, '2022-04-01', 18),
 ('Structure and Interpretation of Computer Programs', 'Harold Abelson, Gerald Jay Sussman', 'MIT Press', '9780262510875', 'SICP: a classic introduction to the art of programming.', '英文', 8500, '1996-07-01', 18),
 ('The C Programming Language, Second Edition', 'Brian W. Kernighan, Dennis M. Ritchie', 'Prentice Hall', '9780131103627', 'K&R: the definitive reference on C by its creators.', '英文', 6700, '1988-04-01', 18),
 ('Clean Code: A Handbook of Agile Software Craftsmanship', 'Robert C. Martin', 'Prentice Hall', '9780132350884', 'Principles and practices of writing readable, maintainable code.', '英文', 5500, '2008-08-01', 18),
 ('Designing Data-Intensive Applications', 'Martin Kleppmann', 'O''Reilly Media', '9781449373320', 'Reliable, scalable and maintainable data systems, explained.', '英文', 6999, '2017-04-01', 18),
 ('代码大全(第2版)', '[美] 史蒂夫·迈克康奈尔', '电子工业出版社', '9787121022982', '软件构建过程的百科全书式指南。', '中文', 12800, '2006-03-01', 18),
 ('设计模式:可复用面向对象软件的基础', '[美] Erich Gamma 等', '机械工业出版社', '9787111075752', 'GoF 二十三种经典设计模式。', '中文', 3500, '2000-09-01', 18),
-- X 环境 / Z 综合
 ('寂静的春天', '[美] 蕾切尔·卡森', '上海译文出版社', '9787532772892', '开启现代环保运动的里程碑之作。', '中文', 3500, '2016-06-01', 21),
 ('大英博物馆世界简史(全3册)', '[英] 尼尔·麦格雷戈', '新星出版社', '9787513313735', '用 100 件馆藏文物讲述两百万年人类史。', '中文', 10800, '2014-01-01', 22);

UPDATE books SET cover_url = 'https://covers.openlibrary.org/b/isbn/' || isbn || '-M.jpg';

-- two copies per title, sequential barcodes; call number = 分类代码-书目编号-册序
INSERT INTO book_copies (book_id, barcode, call_number, location, status)
SELECT b.id,
       'LB' || lpad(((b.id - 1) * 2 + s.n)::text, 6, '0'),
       c.code || '-' || lpad(b.id::text, 4, '0') || '-' || s.n,
       CASE WHEN c.code IN ('I', 'J', 'K') THEN '总馆二层人文区'
            WHEN c.code = 'T'              THEN '总馆三层科技区'
            ELSE '总馆一层综合区' END,
       'IN_LIBRARY'
FROM books b
JOIN categories c ON c.id = b.category_id
CROSS JOIN (SELECT 1 AS n UNION ALL SELECT 2) s;

-- ---------- papers (real publications, real DOIs) ----------

INSERT INTO papers (title, authors, venue, year, pages, abstract, doi, url) VALUES
 ('Deep Residual Learning for Image Recognition', 'Kaiming He, Xiangyu Zhang, Shaoqing Ren, Jian Sun', 'CVPR', 2016, '770-778', '提出残差学习框架 ResNet,使超深网络的训练成为可能,并赢得 ILSVRC 2015。', '10.1109/CVPR.2016.90', 'https://doi.org/10.1109/CVPR.2016.90'),
 ('BERT: Pre-training of Deep Bidirectional Transformers for Language Understanding', 'Jacob Devlin, Ming-Wei Chang, Kenton Lee, Kristina Toutanova', 'NAACL', 2019, '4171-4186', '双向 Transformer 预训练语言模型,刷新十一项 NLP 任务纪录。', '10.18653/v1/N19-1423', 'https://doi.org/10.18653/v1/N19-1423'),
 ('Momentum Contrast for Unsupervised Visual Representation Learning', 'Kaiming He, Haoqi Fan, Yuxin Wu, Saining Xie, Ross Girshick', 'CVPR', 2020, '9729-9738', 'MoCo:以动量编码器与队列字典实现对比自监督视觉表征学习。', '10.1109/CVPR42600.2020.00975', 'https://doi.org/10.1109/CVPR42600.2020.00975'),
 ('Attention Is All You Need', 'Ashish Vaswani, Noam Shazeer, Niki Parmar, Jakob Uszkoreit, Llion Jones, Aidan N. Gomez, Lukasz Kaiser, Illia Polosukhin', 'NeurIPS', 2017, '5998-6008', '提出完全基于注意力机制的 Transformer 架构。', '10.48550/arXiv.1706.03762', 'https://arxiv.org/abs/1706.03762'),
 ('ImageNet Classification with Deep Convolutional Neural Networks', 'Alex Krizhevsky, Ilya Sutskever, Geoffrey E. Hinton', 'NeurIPS / CACM', 2012, '84-90', 'AlexNet:深度卷积网络在 ImageNet 上的突破,引爆深度学习浪潮。', '10.1145/3065386', 'https://doi.org/10.1145/3065386'),
 ('Generative Adversarial Networks', 'Ian J. Goodfellow, Jean Pouget-Abadie, Mehdi Mirza, Bing Xu, David Warde-Farley, Sherjil Ozair, Aaron Courville, Yoshua Bengio', 'NeurIPS', 2014, '2672-2680', '生成器与判别器对抗训练的生成模型框架 GAN。', '10.48550/arXiv.1406.2661', 'https://arxiv.org/abs/1406.2661'),
 ('Adam: A Method for Stochastic Optimization', 'Diederik P. Kingma, Jimmy Ba', 'ICLR', 2015, NULL, '自适应矩估计优化器 Adam,深度学习最常用的优化算法之一。', '10.48550/arXiv.1412.6980', 'https://arxiv.org/abs/1412.6980'),
 ('ImageNet: A Large-Scale Hierarchical Image Database', 'Jia Deng, Wei Dong, Richard Socher, Li-Jia Li, Kai Li, Li Fei-Fei', 'CVPR', 2009, '248-255', '大规模层级图像数据集 ImageNet,现代计算机视觉的基石。', '10.1109/CVPR.2009.5206848', 'https://doi.org/10.1109/CVPR.2009.5206848'),
 ('Deep Learning', 'Yann LeCun, Yoshua Bengio, Geoffrey Hinton', 'Nature', 2015, '436-444', '三位图灵奖得主对深度学习的综述。', '10.1038/nature14539', 'https://doi.org/10.1038/nature14539'),
 ('Language Models are Few-Shot Learners', 'Tom B. Brown et al.', 'NeurIPS', 2020, '1877-1901', 'GPT-3:1750 亿参数语言模型的小样本学习能力。', '10.48550/arXiv.2005.14165', 'https://arxiv.org/abs/2005.14165');

-- ---------- historical loans (migrated from the 2017 legacy dataset, all returned) ----------

WITH m(title, username, loaned, returned) AS (VALUES
 ('大雪中的山庄',     'zhanghua',     '2017-03-15', '2017-06-16'),
 ('三生三世 十里桃花', 'wangxiaowei',  '2017-06-10', '2017-09-02'),
 ('何以笙箫默',       'zhangminghua', '2017-06-12', '2017-09-02'),
 ('11处特工皇妃',     'zhanghua',     '2017-03-15', '2017-09-03'),
 ('人类简史:从动物到上帝', 'wangwaner', '2017-06-15', '2017-09-01'),
 ('明朝那些事儿(1-9)', 'zhanghua',    '2017-06-15', '2017-09-05'),
 ('大雪中的山庄',     'wangxiaowei',  '2017-09-02', '2017-09-02')
)
INSERT INTO loans (copy_id, reader_id, loaned_at, due_at, returned_at, operator_id)
SELECT (SELECT min(bc.id) FROM book_copies bc JOIN books b ON b.id = bc.book_id WHERE b.title = m.title),
       (SELECT u.id FROM users u WHERE u.username = m.username),
       m.loaned::timestamptz,
       m.loaned::timestamptz + CASE WHEN p.reader_type = 'TEACHER' THEN interval '60 days' ELSE interval '30 days' END,
       m.returned::timestamptz,
       (SELECT id FROM users WHERE username = 'admin')
FROM m
JOIN users u ON u.username = m.username
JOIN reader_profiles p ON p.user_id = u.id;
