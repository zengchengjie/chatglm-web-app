# SSL证书目录

此目录用于存放SSL证书文件。

## 文件说明

- `cert.pem` - SSL证书文件
- `key.pem` - SSL私钥文件

## 获取证书

您可以通过以下方式获取SSL证书：

1. 使用Let's Encrypt免费证书
2. 从证书颁发机构购买证书
3. 使用自签名证书（仅用于测试）

## Let's Encrypt证书

如果您使用Let's Encrypt，可以使用Certbot获取证书：

```bash
sudo certbot certonly --standalone -d your-domain.com
```

然后将证书复制到此目录：

```bash
sudo cp /etc/letsencrypt/live/your-domain.com/fullchain.pem ./ssl/cert.pem
sudo cp /etc/letsencrypt/live/your-domain.com/privkey.pem ./ssl/key.pem
```

## 自签名证书（仅用于测试）

如果您只是测试，可以使用OpenSSL生成自签名证书：

```bash
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout ./ssl/key.pem \
  -out ./ssl/cert.pem
```

注意：自签名证书会导致浏览器显示安全警告，不适合生产环境使用。
