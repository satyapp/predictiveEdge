# Login module local setup

The pre-broker login module supports registration, dual OTP verification, email/password login, authenticated profile access and logout.

## Delivery configuration

- Email OTP uses Amazon SES through AWS SDK for Java v2.
- Mobile OTP uses `MockSmsOtpSender` until DLT and the production SMS provider are approved.
- The mock mobile OTP is returned only by the registration API while `PE_SMS_PROVIDER=mock`.
- Application startup fails if the `prod` Spring profile is combined with `PE_SMS_PROVIDER=mock`.

Copy `.env.example` to `.env` and set the identity secrets, a verified SES sender, the SES region and development AWS credentials. Prefer an AWS role or workload identity outside local development. Never commit real credentials or OTP/token secrets.

When the SES account is in the sandbox, sender and recipient addresses must satisfy the account's SES verification restrictions. Moving SES out of the sandbox is an AWS account operation, not application code.

## Run

```powershell
docker compose up --build -d
docker compose ps
```

Open `http://localhost:3000`. The direct API health endpoint is `http://localhost:8080/api/health`; the Nginx-proxied endpoint is `http://localhost:3000/api/health`.

## Broker boundary

OTP delivery is synchronous in this phase. `EmailOtpSender` and `SmsOtpSender` are application ports. A later broker/outbox phase will replace the synchronous call path without changing the public registration and verification endpoints.
