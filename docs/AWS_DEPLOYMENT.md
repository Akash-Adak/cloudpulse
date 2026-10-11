# AWS deployment guide (cost-conscious, single EC2 instance)

> **Status:** these are instructions to run yourself. Nothing here has been executed or verified against a live AWS account.
> **Pricing/eligibility:** AWS Free Tier terms have changed over time and depend on when your account was created.
> Check *Billing → Free Tier* and the EC2 pricing page for your account before launching anything, and set a billing alert.

## Target architecture

```
Browser ─▶ Vercel (React build)
   │  https://api.<your-domain>
   ▼
EC2 (Amazon Linux 2023, small instance)
  ├─ Caddy :80/:443  (automatic HTTPS)
  ├─ backend :8080   (Docker, localhost only behind Caddy)
  └─ MySQL           (Docker volume, not exposed)
Logs ─▶ CloudWatch Logs (/cloudpulse/backend, /cloudpulse/mysql)
Deploys ─▶ GitHub Actions (OIDC) ─▶ SSM Run Command ─▶ EC2   (no SSH needed)
```

**MySQL on the same EC2 vs. RDS:** local MySQL costs nothing extra but you own backups and patching and lose data if the
volume is lost. RDS gives managed backups and isolation, but it is billed separately, so check whether your account's free offer covers it.
For a portfolio project the Docker MySQL on an EBS volume is the minimum-cost choice. Take occasional snapshots of the volume.

Possible charges to watch: EC2 instance hours after any free allowance, EBS storage, public IPv4 address, data transfer out, CloudWatch Logs ingestion and storage, and RDS if you choose it.

## 1. Network and security
- Use the default VPC or a new one with a public subnet.
- **Security group** for the instance: inbound **80 and 443 from anywhere**; **no inbound 22**. Use SSM Session Manager for shell access. If you must use SSH, restrict it to your own IP `/32`.
- MySQL is never published (`mysql` has no `ports:` in compose), so it is reachable only from the Docker network.
- The backend publishes `127.0.0.1:8081` only; public traffic goes through Caddy with TLS.

## 2. IAM
**Instance role** (attach as instance profile):
- `AmazonSSMManagedInstanceCore` (lets SSM run commands and open sessions)
- an inline policy for logs:
```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Action": ["logs:CreateLogGroup", "logs:CreateLogStream", "logs:PutLogEvents", "logs:DescribeLogStreams"],
    "Resource": "arn:aws:logs:*:*:log-group:/cloudpulse/*:*"
  }, {
    "Effect": "Allow",
    "Action": ["cloudwatch:PutMetricData"],
    "Resource": "*",
    "Condition": {"StringEquals": {"cloudwatch:namespace": "CloudPulse"}}
  }]
}
```
**GitHub OIDC (no AWS keys in GitHub):**
1. IAM → Identity providers → add `token.actions.githubusercontent.com`, audience `sts.amazonaws.com`.
2. Create a role `cloudpulse-github-deploy` with this trust policy (replace placeholders):
```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Principal": {"Federated": "arn:aws:iam::<ACCOUNT_ID>:oidc-provider/token.actions.githubusercontent.com"},
    "Action": "sts:AssumeRoleWithWebIdentity",
    "Condition": {
      "StringEquals": {"token.actions.githubusercontent.com:aud": "sts.amazonaws.com"},
      "StringLike": {"token.actions.githubusercontent.com:sub": "repo:<GITHUB_USER>/<REPO>:ref:refs/heads/main"}
    }
  }]
}
```
3. Permissions: `ssm:SendCommand` on the document `AWS-RunShellScript` and on your instance ARN, plus `ssm:GetCommandInvocation` and `ssm:ListCommandInvocations`.
4. In GitHub → Settings → Secrets and variables → Actions: secret `AWS_DEPLOY_ROLE_ARN`; variables `AWS_REGION`, `EC2_INSTANCE_ID`.

## 3. Launch and prepare the instance
Launch Amazon Linux 2023, attach the instance profile and the security group above, and use the smallest instance your account's free offer allows.
Because Maven builds need memory on tiny instances, add swap first. Then in an SSM session:
```bash
sudo dd if=/dev/zero of=/swapfile bs=1M count=2048 && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
sudo dnf install -y docker git
sudo systemctl enable --now docker
sudo mkdir -p /usr/local/lib/docker/cli-plugins
sudo curl -SL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 -o /usr/local/lib/docker/cli-plugins/docker-compose
sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
sudo git clone https://github.com/<GITHUB_USER>/<REPO>.git /opt/cloudpulse
cd /opt/cloudpulse && sudo cp .env.example .env && sudo nano .env
```
In `.env`: strong passwords and JWT secret (never reuse local ones), `CLOUDPULSE_ALLOW_PRIVATE_TARGETS=false`,
`CLOUDPULSE_CORS_ALLOWED_ORIGINS=https://<your-vercel-app>.vercel.app`, `AWS_REGION=<region>`, and `API_DOMAIN`.
`.env` stays on the server and is in `.gitignore`. (For stronger secret handling use SSM Parameter Store and fetch values at deploy time.)

**HTTPS domain:** point a DNS `A` record for `api.<your-domain>` at the instance's public IP (allocate an Elastic IP so it does not change).
No domain? `<ip-with-dashes>.sslip.io` (e.g. `13-233-1-2.sslip.io`) resolves to that IP and Caddy can issue a certificate for it.

## 4. First deployment
```bash
cd /opt/cloudpulse
sudo docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build mysql backend caddy
sudo docker compose -f docker-compose.yml -f docker-compose.prod.yml ps
curl -fsS http://127.0.0.1:8081/actuator/health          # {"status":"UP"}
curl -fsS https://$API_DOMAIN/actuator/health            # via HTTPS
```
Later deployments: GitHub → Actions → **Deploy to EC2** → Run workflow.

## 5. Frontend on Vercel
Import the repo, set **Root Directory** to `frontend`, add the env var `VITE_API_BASE_URL=https://<API_DOMAIN>`, deploy.
Then make sure `CLOUDPULSE_CORS_ALLOWED_ORIGINS` on the server contains the Vercel URL and redeploy the backend.

## 6. CloudWatch
- **Logs:** the `awslogs` driver in `docker-compose.prod.yml` ships container logs to `/cloudpulse/backend` and `/cloudpulse/mysql`. Set retention to limit cost:
  `aws logs put-retention-policy --log-group-name /cloudpulse/backend --retention-in-days 14`
- **Custom metrics from CloudPulse itself** (run on the instance, e.g. every 5 min via cron):
  `CLOUDPULSE_URL=http://127.0.0.1:8081 CLOUDPULSE_ADMIN_USERNAME=... CLOUDPULSE_ADMIN_PASSWORD=... python3 scripts/cloudpulse_check.py --cloudwatch --region <region>` (needs `pip install boto3`).
- **Alarms:**
```bash
aws cloudwatch put-metric-alarm --alarm-name cloudpulse-services-down \
  --namespace CloudPulse --metric-name ServicesDown --statistic Maximum \
  --period 300 --evaluation-periods 1 --threshold 1 --comparison-operator GreaterThanOrEqualToThreshold \
  --treat-missing-data notBreaching
aws cloudwatch put-metric-alarm --alarm-name cloudpulse-ec2-status \
  --namespace AWS/EC2 --metric-name StatusCheckFailed --dimensions Name=InstanceId,Value=<INSTANCE_ID> \
  --statistic Maximum --period 300 --evaluation-periods 2 --threshold 1 --comparison-operator GreaterThanOrEqualToThreshold
```
Add an SNS topic with `--alarm-actions` if you want e-mail notifications.

## 7. Verification checklist
- [ ] `curl https://<API_DOMAIN>/actuator/health` returns `UP`
- [ ] `curl -i https://<API_DOMAIN>/api/services` returns `401` without a token
- [ ] Login works from the Vercel site and the dashboard shows data
- [ ] Port 3306 and 8080 are not reachable from the internet (`nmap` or try from another machine)
- [ ] Add a real service; a check appears within the interval
- [ ] CloudWatch → Log groups shows `/cloudpulse/backend` receiving events
- [ ] Deploy workflow completes green

## 8. Rollback
```bash
cd /opt/cloudpulse && sudo git log --oneline -5
sudo git checkout <previous-commit>
sudo docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build mysql backend caddy
```
(Database migrations are forward-only. Take an EBS snapshot before deploying schema changes.) Return to `main` afterwards with `git checkout main`.

## 9. Cleanup (stop all billing)
1. Terminate the EC2 instance. Delete its EBS volumes and any snapshots/AMIs.
2. Release the Elastic IP.
3. Delete log groups: `aws logs delete-log-group --log-group-name /cloudpulse/backend` (and `/cloudpulse/mysql`).
4. Delete CloudWatch alarms and any SNS topic.
5. Delete the IAM role, instance profile and OIDC provider if unused.
6. Check *Billing → Bills* the next day.
