<?php

namespace App\Controller;

use App\Service\BackendApiClient;
use Psr\Log\LoggerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;
use Symfony\Contracts\HttpClient\Exception\TransportExceptionInterface;

final class HomeController extends AbstractController
{
    private const MODULE_KEYS = [
        'accounting' => 'accounting',
        'crm' => 'crm',
        'sales' => 'sales',
        'purchase' => 'purchase',
        'inventory' => 'inventory',
        'manufacturing' => 'manufacturing',
        'promotions' => 'promo-campaigns',
        'pos' => 'pos',
        'hr' => 'hr',
        'documents' => 'documents',
        'project' => 'projects',
        'helpdesk' => 'helpdesk',
        'website' => 'website',
        'ecommerce' => 'catalog',
        'marketing' => 'marketing',
        'planning' => 'planning',
        'dashboard' => 'dashboard',
    ];

    /** @var array<string, array{category: string, title: string, subtitle: string, description: string, icon: string, badge?: string}> */
    private const MODULE_CATALOG = [
        'base' => ['category' => 'core', 'title' => 'Base', 'subtitle' => 'Uživatelé, role a společnosti', 'description' => 'Centrální správa ERP instance. Nastavte, kdo má přístup ke kterým datům a modulům.', 'icon' => 'E'],
        'accounting' => ['category' => 'core', 'title' => 'Účetnictví', 'subtitle' => 'Finance a doklady', 'description' => 'Účetní agenda, faktury, bankovní pohyby a finanční přehledy na jednom místě.', 'icon' => '∿'],
        'crm' => ['category' => 'sales', 'title' => 'CRM', 'subtitle' => 'Příležitosti a zákazníci', 'description' => 'Sledujte obchodní příležitosti od prvního kontaktu až po uzavřenou objednávku.', 'icon' => '◆'],
        'sales' => ['category' => 'sales', 'title' => 'Prodej', 'subtitle' => 'Nabídky a objednávky', 'description' => 'Tvorba nabídek, objednávek a cenových pravidel pro zákazníky.', 'icon' => '▥'],
        'purchase' => ['category' => 'operations', 'title' => 'Nákup', 'subtitle' => 'Dodavatelé a nákupní objednávky', 'description' => 'Řiďte dodavatele, poptávky, nákupní objednávky a příjem zboží.', 'icon' => '═'],
        'inventory' => ['category' => 'operations', 'title' => 'Sklad', 'subtitle' => 'Zásoby a pohyb zboží', 'description' => 'Stav zásob, skladové lokace, příjem, výdej a inventury v reálném čase.', 'icon' => '⬡'],
        'manufacturing' => ['category' => 'operations', 'title' => 'Výroba', 'subtitle' => 'Plánování a výrobní příkazy', 'description' => 'Plánujte výrobu, kusovníky, pracovní postupy a spotřebu materiálu.', 'icon' => '▰'],
        'promotions' => ['category' => 'sales', 'title' => 'Promo kampaně', 'subtitle' => 'Akce a letákové nabídky', 'description' => 'Plánujte promo akce, alokujte zboží do prodejen a sledujte prodeje i příspěvky dodavatelů.', 'icon' => '✦', 'badge' => 'MVP'],
        'pos' => ['category' => 'sales', 'title' => 'Pokladna', 'subtitle' => 'Prodej na prodejně', 'description' => 'Rychlé pokladní rozhraní, směny, platby a denní uzávěrky pro prodejny.', 'icon' => '▤'],
        'hr' => ['category' => 'core', 'title' => 'Lidé', 'subtitle' => 'Zaměstnanci a docházka', 'description' => 'Evidence zaměstnanců, týmů, pracovních rolí, dovolených a přístupů.', 'icon' => '●'],
        'documents' => ['category' => 'core', 'title' => 'Dokumenty', 'subtitle' => 'Soubory a schvalování', 'description' => 'Ukládejte dokumenty u záznamů, nastavte schvalovací kroky a najděte vše během okamžiku.', 'icon' => '▣'],
        'project' => ['category' => 'operations', 'title' => 'Projekty', 'subtitle' => 'Úkoly a termíny', 'description' => 'Rozdělte práci do projektů, úkolů a milníků. Sledujte odpovědnosti i termíny.', 'icon' => '◢'],
        'helpdesk' => ['category' => 'operations', 'title' => 'Helpdesk', 'subtitle' => 'Požadavky a podpora', 'description' => 'Přijímejte požadavky, přiřazujte je týmům a dodržujte SLA vůči interním i externím zákazníkům.', 'icon' => '＋'],
        'website' => ['category' => 'sales', 'title' => 'Web', 'subtitle' => 'Veřejný web a obsah', 'description' => 'Spravujte obsah webu, katalog a zákaznické formuláře napojené na ERP data.', 'icon' => '◓'],
        'ecommerce' => ['category' => 'sales', 'title' => 'eCommerce', 'subtitle' => 'Katalog a online obchod', 'description' => 'Navrhněte úvodní stránku, spravujte kategorie a ověřte skladovou dostupnost i doručení.', 'icon' => '◫', 'badge' => 'NOVÉ'],
        'marketing' => ['category' => 'sales', 'title' => 'Marketing', 'subtitle' => 'Kampaně a komunikace', 'description' => 'Segmentujte zákazníky, připravujte kampaně a měřte jejich dopad na obchod.', 'icon' => '➤'],
        'planning' => ['category' => 'operations', 'title' => 'Plánování', 'subtitle' => 'Směny a kapacity', 'description' => 'Plánujte směny, zdroje a kapacity týmů tak, aby provoz držel krok s poptávkou.', 'icon' => '◀'],
        'dashboard' => ['category' => 'core', 'title' => 'Dashboard', 'subtitle' => 'Přehledy a KPI', 'description' => 'Sestavte si pracovní přehled z klíčových ukazatelů napříč všemi moduly ERP.', 'icon' => '▦'],
    ];

    #[Route('/', name: 'app_root', methods: ['GET'])]
    public function root(): Response
    {
        return $this->redirectToRoute('app_home');
    }

    #[Route('/apps', name: 'app_home', methods: ['GET'])]
    public function index(Request $request, BackendApiClient $backend, LoggerInterface $logger): Response
    {
        $allowed = [];
        $error = (string) $request->query->get('error', '');
        try {
            $access = $backend->json('GET', '/api/v1/auth/me');
            if (($access['administrator'] ?? false) === true) {
                $allowed = array_keys(self::MODULE_KEYS);
                $allowed[] = 'base';
            } else {
                $modules = $access['modules'] ?? [];
                if (is_array($modules)) {
                    foreach (self::MODULE_KEYS as $tile => $key) {
                        if (in_array($key, $modules, true)) {
                            $allowed[] = $tile;
                        }
                    }
                    if (array_intersect($modules, ['companies', 'users', 'roles', 'settings']) !== []) {
                        $allowed[] = 'base';
                    }
                }
            }
        } catch (TransportExceptionInterface $exception) {
            $logger->error('Could not load module permissions from ERP backend.', ['exception' => $exception]);
            $error = 'Backend není dostupný, oprávnění modulů se nepodařilo načíst.';
        } catch (\App\Service\BackendApiException $exception) {
            $logger->error('ERP backend rejected module permission lookup.', ['exception' => $exception]);
            $error = 'Oprávnění modulů se nepodařilo načíst (HTTP '.$exception->statusCode.').';
        } catch (\UnexpectedValueException $exception) {
            $logger->error('ERP backend returned invalid module permissions.', ['exception' => $exception]);
            $error = 'Backend vrátil neplatná data oprávnění.';
        }

        return $this->render('home/index.html.twig', [
            'allowedModules' => $allowed,
            'moduleCatalog' => self::MODULE_CATALOG,
            'error' => $error,
        ]);
    }
}
