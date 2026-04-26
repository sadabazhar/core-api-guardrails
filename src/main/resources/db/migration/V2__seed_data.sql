-- Seeding user and bot for api testing

INSERT INTO users (username, is_premium) VALUES
('sadab_dev', true),
('rahul_dev', false),
('anita_codes', true),
('john_backend', false),
('neha_ui', false),
('amit_java', true),
('priya_dev', false),
('vikas_engineer', false),
('sneha_ai', true),
('arjun_fullstack', false);

INSERT INTO bots (name, persona_description) VALUES
('HelperBot', 'Provides helpful answers to user queries'),
('MotivatorBot', 'Encourages users with motivational replies'),
('SarcasticBot', 'Responds with witty sarcasm'),
('TechGuruBot', 'Gives technical explanations'),
('NewsBot', 'Shares trending news summaries'),
('FitnessBot', 'Provides fitness advice'),
('FinanceBot', 'Shares financial tips'),
('StudyBot', 'Helps with academic questions'),
('JokeBot', 'Tells random jokes'),
('DebateBot', 'Engages in logical debates');