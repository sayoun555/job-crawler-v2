const dotenv = require('dotenv');
const path = require('path');

// .env 파일 로드
dotenv.config({ path: path.join(__dirname, '.env') });

module.exports = {
    apps: [
        {
            name: 'job-crawler',
            script: 'java',
            args: [
                '-Xmx384m', '-Xms256m',
                '-jar', 'job-crawler/build/libs/job-crawler-0.0.1-SNAPSHOT.jar',
                '--spring.profiles.active=prod',
            ].join(' '),
            cwd: __dirname,
            env: {
                SPRING_PROFILES_ACTIVE: 'prod',
                DB_USERNAME: process.env.DB_USERNAME,
                DB_PASSWORD: process.env.DB_PASSWORD,
                JWT_SECRET: process.env.JWT_SECRET,
                ENCRYPTION_KEY: process.env.ENCRYPTION_KEY,
                ADMIN_EMAIL: process.env.ADMIN_EMAIL,
                ADMIN_PASSWORD: process.env.ADMIN_PASSWORD,
                OPENCLAW_API_URL: process.env.OPENCLAW_API_URL,
                OPENCLAW_API_MODEL: process.env.OPENCLAW_API_MODEL,
            },
            max_restarts: 10,
            restart_delay: 5000,
            log_date_format: 'YYYY-MM-DD HH:mm:ss',
            error_file: './logs/job-crawler-error.log',
            out_file: './logs/job-crawler-out.log',
            merge_logs: true,
        },
        {
            name: 'job-frontend',
            script: 'pnpm',
            args: 'start',
            cwd: __dirname + '/job-frontend',
            env: {
                PORT: 3000,
                NODE_ENV: 'production',
            },
            max_restarts: 10,
            restart_delay: 3000,
            error_file: './logs/frontend-error.log',
            out_file: './logs/frontend-out.log',
            merge_logs: true,
        },
    ],
};
