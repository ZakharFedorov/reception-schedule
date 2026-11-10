# Reception Schedule

Webová aplikace pro plánování směn a výpočet odměn na recepcích kolejí TUL.
Vzniká jako diplomová práce. Backend ve Spring Bootu umožňuje správci
bloku přidělit zaměstnance na celou směnu nebo na její část.

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

- [Koncepce aplikace](docs/koncepce.md) popisuje směny, záskoky mezi bloky, pravidla odpočinku a výpočet měsíčních odměn.
- [Datový model rozpisu směn](docs/datovy-model.md) vysvětluje vztah mezi blokem, zaměstnancem, směnou a přidělenou částí směny.
- [První migrace](backend/src/main/resources/db/migration/V1__create_schedule_core.sql) vytváří tabulky a jejich omezení.
- [Ukázková data](dev/demo.sql) ilustrují záskok mezi dvěma bloky. Skript je určený pro jednorázové spuštění nad prázdnou databází po migraci:

```powershell
Get-Content -Raw dev/demo.sql | docker compose exec -T db psql -v ON_ERROR_STOP=1 -U reception -d reception
```

Pro ukončení lokálního běhu použij `docker compose down`. Data se uchovávají v pojmenovaném svazku.

## Ruční přidělení směny

Správce může přidělit zaměstnance z libovolného bloku na směnu svého bloku.
`POST /api/shifts/{shiftId}/assignments` přijímá `employeeId`, `startsAt`
a `endsAt`. Oba časy musí obsahovat posun vůči UTC. Úspěšný požadavek vrátí
HTTP 201 s ID přidělení. Přesah mimo směnu vrátí 400, chybějící oprávnění
403, neznámá směna nebo zaměstnanec 404 a překryv 409.

Pro místní vyzkoušení slouží HTTP Basic a CSRF token vázaný na relaci.
Po načtení `dev/demo.sql` spusť jednou následující příkaz. Přidá dvě
testovací směny a místní účty správců A a B. Skript patří pouze do vývojové databáze.

```powershell
Get-Content -Raw dev/demo2.sql | docker compose exec -T db psql -v ON_ERROR_STOP=1 -U reception -d reception
```

Skript vypíše ID Anny a obou směn. Příklad pro správce B:

```powershell
$shiftId = Read-Host 'ID směny bloku B'
$employeeId = [long](Read-Host 'ID zaměstnance')
$api = 'http://localhost:8080/api'
$auth = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes('admin-b@example.test:local-demo-only'))
$headers = @{ Authorization = "Basic $auth" }
$csrf = Invoke-RestMethod "$api/csrf" -Headers $headers -SessionVariable session
$headers[$csrf.headerName] = $csrf.token
$body = @{ employeeId = $employeeId; startsAt = '2026-11-11T19:00:00+01:00'; endsAt = '2026-11-11T22:00:00+01:00' } | ConvertTo-Json
Invoke-RestMethod "$api/shifts/$shiftId/assignments" -Method Post -ContentType 'application/json' -Body $body -Headers $headers -WebSession $session
```

Místní účty slouží k ověření této části API. Napojení na TUL, kontrola
odpočinku a měsíčních limitů zůstávají součástí dalšího vývoje.

## Testy

Z adresáře `backend` spusť `mvn test` nebo použij Maven v IntelliJ IDEA.
Testy HTTP a oprávnění používají MockMvc. Test souběhu navíc vyžaduje
samostatnou databázi PostgreSQL. Na lokálním Compose ji vytvoř jednou:

```powershell
docker compose exec db createdb -U reception reception_test
$env:RECEPTION_TEST_DB_URL = 'jdbc:postgresql://localhost:5432/reception_test'
```

Spusť `mvn test` ve stejném terminálu, nebo nastav tuto proměnnou v konfiguraci
testu `ConcurrentAssignmentTest` v IDEA. U jiné instance nastav také
`DB_USER` a `DB_PASSWORD`. Test spustí migrace v samostatném schématu,
odešle dva souběžné požadavky a ověří výsledky 201 a 409 i počet záznamů.
Po testu odstraní své schéma. Bez testovací URL se jen tento test přeskočí.
