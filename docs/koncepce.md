# Koncepce plánování směn a výpočtu odměn na recepcích kolejí TUL

Tento dokument popisuje, jak má aplikace sloužit zaměstnancům recepcí kolejí Technické univerzity v Liberci. Vychází z práce na šesti blocích A až F. Správce každého bloku sestavuje rozpis své recepce a zaměstnanci podle něj pracují. Tentýž rozpis má být podkladem pro kontrolu pracovní doby i pro výpočet měsíční odměny.

První verze počítá s ručním plánováním. Správce vybere zaměstnance a čas, aplikace zkontroluje přidělení a upozorní ho na konkrétní překážku. Rozsah už implementovaných funkcí a postup pro jejich vyzkoušení popisuje [README](../README.md).

## Směna na recepci

Směna určuje, na kterém bloku a od kdy do kdy má být recepce obsazená. Přidělení směny určuje, kdo na recepci pracuje a kterou část směny pokrývá. Časy v příkladech odpovídají místnímu času v Liberci.

| Směna | Začátek | Konec |
|---|---|---|
| Ranní | 06:00 | 14:00 |
| Odpolední | 14:00 | 22:00 |
| Noční | 22:00 | 06:00 následujícího dne |

Na standardní směně se počítá s jedním člověkem v daný okamžik. Pokud odpolední směnu převezme Anna od 14:00 do 19:00, úsek od 19:00 do 22:00 je ještě volný. Jakmile jej převezme kolega, směna je plně obsazená. Obě přidělení se mohou přesně v 19:00 dotýkat. Každé musí celé ležet uvnitř směny.

**Pevné ranní směny.** Od pondělí do pátku obvykle slouží ráno vedoucí recepce. V kalendáři i týdenním rozpisu má být taková směna předem obsazená. Připadne-li na tento den svátek, ranní směna se uvolní k běžnému přidělení. Správce může zadat výjimku také pro konkrétní datum, například při nepřítomnosti vedoucího. Podle potřeby může na daný den doplnit i pracovní interval s jiným začátkem a koncem.

## Domovský blok a záskok

Každý zaměstnanec patří právě k jednomu týmu. Domovský blok má vlastní seznam zaměstnanců a vlastní rozpis. Správce může na celou směnu nebo její část vybrat také zaměstnance jiného bloku.

Anna má domovský blok A. Dne 11. listopadu může mít tato dvě přidělení:

| Čas | Kde Anna pracuje | Kdo ji může přidělit |
|---|---|---|
| 14:00–19:00 | Recepce bloku A | Správce bloku A |
| 19:00–22:00 | Recepce bloku B | Správce bloku B |

Správce B vidí ve svém rozpisu Annu od 19:00. Kdyby její záskok začínal už v 18:59, musela by jednu minutu sloužit na dvou recepcích zároveň. Takové přidělení aplikace odmítne. Při předání v 19:00 na sebe oba úseky navazují a pro posouzení odpočinku tvoří jeden souvislý pracovní úsek od 14:00 do 22:00.

Anna dál patří do týmu A. V osobním rozpisu uvidí obě přidělení. Hodiny z obou bloků se zohlední v jejím měsíčním limitu, při kontrole odpočinku i při výpočtu odměny. Správce B může upravit záskok na své recepci. Osobní nastavení Anny, včetně jejího limitu, spravuje její domovský blok.

Do odevzdání podkladů pro výpočet odměn může správce upravit rozpis podle toho, kdo směnu skutečně odpracoval. Pokud zaměstnanec odpracoval jen část směny, správce upraví jeho pracovní interval a případný záskok připíše kolegovi, který ho vystřídal. Aplikace podle upraveného rozpisu přepočítá hodiny i odměny. Po odevzdání podkladů už rozpis za příslušné období nelze měnit. Samostatná evidence docházky se nevede.
## Zaměstnanec a správce bloku

| Role | Oprávnění v navržené aplikaci |
|---|---|
| Zaměstnanec | Prohlíží si rozpis a seznam kolegů svého bloku, vlastní směny na všech blocích, svůj profil, hodiny a odměnu. |
| Správce bloku | Má také práva zaměstnance. Na svém bloku přidává pracovní intervaly, přiděluje směny, mění nebo ruší přidělení, ukládá a zveřejňuje rozpis. U svého týmu spravuje limity a údaje pro výpočet odměn a vidí měsíční výpočty. |

Blok může mít nejvýše jednoho správce. Pokud žádného nemá, může se jím na stránce **Tým** stát první ověřený zaměstnanec bloku, který o práva úspěšně požádá. Příslušnost zaměstnance k bloku se musí ověřit. Způsob tohoto ověření je třeba dohodnout s pracovištěm.

Stávající správce může předat oprávnění jinému zaměstnanci svého týmu. Předchozímu správci poté zůstanou práva zaměstnance. Server musí zajistit, aby ani dvě současné žádosti nevedly ke jmenování dvou správců. Koncepce počítá s těmito dvěma rolemi.

## Co se kontroluje při plánování

Při vytvoření, změně i potvrzení přidělení má aplikace prověřit pracovní dobu zaměstnance na všech blocích. Kontrola zahrnuje také přidělení v sousedním týdnu nebo měsíci, pokud ovlivňují posuzovaný interval.

| Pravidlo plánování | Co musí rozpis splnit |
|---|---|
| Překryvy | Jeden zaměstnanec může být v daný okamžik na jedné směně. Na jednom místě se mohou zaměstnanci postupně vystřídat. |
| Denní odpočinek | Model počítá s alespoň 11 hodinami nepřetržitého odpočinku v rámci 24 po sobě jdoucích hodin. Dvě sousední osmihodinové směny nelze spojit v šestnáctihodinovou službu. |
| Týdenní odpočinek | Pro dospělého zaměstnance model počítá s nejméně 35 hodinami nepřetržitého odpočinku týdně. |
| Měsíční limit | Každý zaměstnanec má vlastní horní hranici. Při limitu 80 hodin a již přidělených 56 hodinách mu lze přidělit nejvýše dalších 24 hodin. Limit neurčuje, kolik hodin zaměstnanec musí odpracovat. |

Při porušení pravidla aplikace změnu odmítne a správci sdělí důvod. Pro ruční plánování platí stejná omezení jako pro budoucí automatický návrh. S výjimkami pro zkrácení denního odpočinku se nepočítá. Před nasazením je třeba ověřit pravidla pracovní doby podle podmínek pracoviště a platných českých předpisů.

## Jak zaměstnanec a správce používají aplikaci

Rozhraní má být v češtině a má fungovat v prohlížeči na počítači i telefonu.

**Registrace a přihlášení.** Pro místní účet se počítá se jménem, příjmením, e-mailem, heslem a domovským blokem. Zaměstnanec se přihlásí e-mailem a heslem. Pro napojení na infrastrukturu TUL se počítá s univerzitním účtem. Konkrétní mechanismus a přístupy je třeba dohodnout s IT oddělením TUL. Napojení je samostatnou součástí diplomového projektu.

**Rozpis směn.** Měsíční kalendář ukáže dny, české svátky, směny, přidělené zaměstnance a další pracovní intervaly. Z označení směny musí být patrné, zda je volná, částečně obsazená, nebo obsazená celá. Po výběru dne zaměstnanec uvidí podrobnosti. Pod kalendářem bude týdenní tabulka se zaměstnanci v řádcích a dny a směnami ve sloupcích. Přechod mezi týdny umožní porovnat zatížení jednotlivých lidí a najít volné úseky. Správce zde upraví přidělení, uloží rozpracovaný návrh a hotový rozpis potvrdí a zveřejní.

**Tým.** U každého člena se zobrazí jméno, domovský blok, oprávnění správce a pro zvolený měsíc také limit, přidělené hodiny a zbývající hodiny do limitu. Správce může otevřít podrobnosti a limit změnit. Na této stránce se také získávají nebo předávají oprávnění správce.

**Můj profil.** Zaměstnanec uvidí své jméno, e-mail, blok, oprávnění, hodinovou sazbu a přehled za vybraný měsíc. Přehled zahrne limit a jeho čerpání, noční, víkendové a sváteční hodiny a rozpis jednotlivých složek odměny.

**Nastavení.** Správce zde pracuje s typy směn, výjimkami pro konkrétní data, pevnými ranními směnami, limity týmu a údaji pro výpočet odměn. U změny přidělení má být možné dohledat, kdo ji provedl a kdy. Pro první verzi stačí zaznamenávat tyto údaje bez samostatné obrazovky s celou historií. U hodinových sazeb a dalších parametrů odměňování je potřeba uchovat dobu platnosti, aby se při výpočtu za minulý měsíc použila tehdejší sazba.

## Jak se počítá odměna

Podkladem jsou pracovní intervaly přidělené konkrétnímu zaměstnanci v rozpisu. Výpočet zohlední jeho hodinovou sazbu, placenou dobu a čas, který připadl na noc, sobotu, neděli nebo svátek. Záskok se započítá tomu, kdo příslušný úsek odpracoval.

**Placená doba při záskoku.** U záskoku v délce nejvýše čtyř hodin se započítá celý interval. Při delším záskoku se odečte půlhodinová přestávka. Za úsek 14:00–18:00 se tedy zaplatí 4 hodiny. Za úsek 14:00–20:00 se zaplatí 5,5 hodiny. Způsob započtení přestávky u celé standardní směny je třeba ověřit podle pravidel pracoviště.

| Příplatek | Předběžná výše |
|---|---|
| Noční práce od 22:00 do 06:00 | 20 % |
| Práce v sobotu nebo v neděli | 25 % |
| Práce ve svátek | 100 % |

Pro konečný výpočet je třeba s pracovištěm ověřit sazby, základ jednotlivých příplatků a pravidla jejich souběhu. Pokud výpočet vyžaduje průměrný hodinový výdělek, musí jej aplikace evidovat vedle základní hodinové sazby.

### Noční směna na přelomu měsíců

Noční směna začíná 31. října ve 22:00 a končí 1. listopadu v 06:00. Podle pravidla recepce se její jednotlivé složky zahrnou do měsíčního výpočtu takto:

| Složka odměny za tuto směnu | Měsíc zúčtování |
|---|---|
| Základní odměna za směnu | Říjen, podle dne nástupu na směnu |
| Příplatek za noční práci | Říjen |
| Příplatek za víkend nebo svátek, pokud na něj u této směny vznikne nárok | Listopad |

Při určení nároku záleží na kalendářním dni a čase práce. Přechod přes půlnoc sám o sobě nezakládá nárok na víkendový nebo sváteční příplatek. Pro statistiku práce ve svátek se počítají hodiny podle skutečného kalendářního data, i když se příplatek zúčtuje v jiném období.

### Co bude v měsíčním přehledu

Za vybraný měsíc aplikace zobrazí přidělené a placené hodiny, základní odměnu, jednotlivé příplatky a celkovou částku. Zaměstnanec může procházet také předchozí měsíce. Správce uvidí výpočty zaměstnanců svého týmu. Počítá se s exportem podkladů pro zpracování odměn. Výpočet čisté mzdy, daní a odvodů je mimo základní rozsah aplikace.

## Ochrana údajů

Přístup k rozpisům a osobním údajům vyžaduje přihlášení. Server kontroluje oprávnění i příslušnost ke konkrétnímu bloku. Zaměstnanec vidí vlastní odměnu, správce může pracovat s údaji svého týmu. Možnost přidělit někoho na záskok z jiného bloku nezakládá právo měnit jeho sazbu nebo limit.

Pro provoz se počítá s připojením přes HTTPS, bezpečnou správou uživatelských relací, ukládáním hesel v podobě bezpečných hashů, záznamem důležitých změn a zálohováním dat.

## Ověření v provozu

Před předáním aplikace zaměstnancům je potřeba ověřit registraci a přihlášení, správu týmu, sestavení a uložení rozpisu, předání směny a výpočet odměny. Při ověřování oprávnění se mají vyzkoušet také zamítnuté operace, například pokus správce A změnit směnu bloku B. Pro kontrolu výpočtů se výsledky porovnají s předem ověřenými příklady včetně záskoků, svátků a noční směny na přelomu měsíců.

Zaměstnanci recepce pak vyzkoušejí funkční aplikaci při praktických úkolech. Budou moci popsat, kde byl rozpis nepřehledný, která operace neodpovídala jejich práci nebo co jim chybělo. Součástí diplomové práce bude vyhodnocení skutečně získané zpětné vazby a popis úprav a dalších možností rozvoje.

## Další rozvoj

**Moje dostupnost.** Zaměstnanec by v kalendáři označil konkrétní směny, které může odpracovat. Například 15. listopadu by mohl vybrat odpolední i noční směnu. Stačí zadávat dostupné směny jednotlivě, bez zvláštního označování nedostupných dnů, dalších kategorií preferencí nebo opakujících se vzorů. Další možností je nabídnout zaměstnancům podání návrhu na výměnu nebo záskok.

**Automatický návrh rozpisu.** Správce by vybral období. Algoritmus by při obsazování směn zohlednil dostupnost zaměstnanců, jejich limity, odpočinek, přidělení na všech blocích a pevné směny vedoucího. Mezi přípustnými variantami by vybíral podle rozložení práce. Pokud některou směnu nelze obsadit, příslušný interval by zůstal viditelně volný. Automatické dělení standardní směny se v této fázi nepředpokládá. Návrh by mohl správce před uložením nebo zveřejněním ručně upravit.

**Rozdělení svátečních hodin.** Při porovnávání přípustných variant se má sledovat počet hodin připadajících na svátky za kalendářní rok. Dvouhodinový záskok a celá směna tak mají v přehledu různou váhu. Po změně rozpisu se statistika přepočítá. Cílem je zmírnit rozdíly v zatížení s ohledem na dostupnost a individuální limity. Přesný způsob hodnocení bude součástí návrhu a testování algoritmu. Další statistiky a úpravy rozhraní mají vycházet také ze zkušeností zaměstnanců při zkušebním provozu.
