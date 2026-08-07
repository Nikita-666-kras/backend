# amoCRM Salesbot — диагностика «не работает»

## Симптом A: в логах только curl (test3), нет реального contact_id

**Бот не вызывает сервер.** API живой, проблема в amo / этапе.

Проверка nginx (были ли POST снаружи кроме вашего curl):

```bash
sudo grep amocrm /var/log/nginx/access.log | tail -20
# или
sudo journalctl -u nginx --since "10 min ago" | grep amocrm
```

В amo проверь:

1. В боте URL ровно: `https://api.atris.site/amocrm/autoar`
2. Бот **сохранён** и **привязан к этапу** воронки (не только открыт в редакторе)
3. Сделку **перевели на этот этап** (смена телефона бота не запускает)
4. У контакта сделки заполнен телефон в поле `1844509`
5. После правок JSON — **импорт/сохранение заново**, не старая копия бота

## Симптом B: в логе есть contact_id и continue ok, поле АР пустое

Второй шаг бота: значение должно быть `{{json.ar}}`, поле `1902113`.  
Если пусто — попробуй `{{json.data.ar}}`.

## Симптом C: continue failed на amocrm.ru

Сеть с VPS до amo, неверный token — смотри текст ERROR в логе.

## После деплоя фикса (sync continue)

```bash
cd /opt/blog-platform
git pull origin master
docker compose build --no-cache integrations-service
docker compose up -d --force-recreate integrations-service

# окно 1
docker compose logs -f integrations-service | grep autoar

# окно 2 / amo: переведи сделку на этап
```

Ожидание при реальном боте:

```text
autoar: contact_id=<число> phone_len=...>0 ar=0440 ... return_url=present token=present
autoar: continue ok ar=0440
```
