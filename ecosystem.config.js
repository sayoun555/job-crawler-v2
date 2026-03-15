module.exports = {
    apps: [
        {
            name: 'job-crawler',
            script: 'java',
            args: '-jar job-crawler/build/libs/job-crawler-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod',
            cwd: __dirname,
            env: {
                SPRING_PROFILES_ACTIVE: 'prod',
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
            script: 'npm',
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
        {
            name: 'cloudflare-tunnel',
            script: 'cloudflared',
            args: 'tunnel run job-crawler',
            max_restarts: 10,
            restart_delay: 3000,
        },
    ],
};
