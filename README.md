# Reception Schedule

Webová aplikace pro plánování směn a výpočet odměn na recepcích kolejí TUL.
Vzniká jako diplomová práce. V této fázi projekt obsahuje základ backendu
ve Spring Bootu a databázový model rozpisu směn v PostgreSQL.

## Lokální spuštění

V kořenové složce projektu spusť s běžícím Dockerem:

```powershell
docker compose up --build -d
docker compose ps
docker compose logs backend
```

Backend používá Javu 21 a naslouchá na portu 8080. PostgreSQL 17 je lokálně
dostupné na portu 5432. Při startu backendu Flyway aplikuje databázové migrace.
Tabulky můžeš vypsat příkazem:

```powershell
docker compose exec db psql -U reception -d reception -c "\dt"
```

## Dokumentace a ukázková data

- [Datový model rozpisu směn](docs/datovy-model.md) vysvětluje vztah mezi blokem, zaměstnancem, směnou a přidělenou částí směny.
- [První migrace](backend/src/main/resources/db/migration/V1__create_schedule_core.sql) vytváří tabulky a jejich omezení.
- [Ukázková data](dev/demo.sql) ilustrují záskok mezi dvěma bloky. Skript je určený pro jednorázové spuštění nad prázdnou databází po migraci:

```powershell
Get-Content -Raw dev/demo.sql | docker compose exec -T db psql -v ON_ERROR_STOP=1 -U reception -d reception
```

Pro ukončení lokálního běhu použij `docker compose down`. Data se uchovávají v pojmenovaném svazku.
