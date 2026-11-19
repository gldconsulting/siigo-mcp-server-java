package co.gldconsulting.siigo.mcp.tool;

import co.gldconsulting.siigo.SiigoApi;
import co.gldconsulting.siigo.mcp.config.ServerConfiguration;
import co.gldconsulting.siigo.service.accounting.*;
import co.gldconsulting.siigo.service.catalog.*;
import co.gldconsulting.siigo.service.reporting.ReportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/** Only these public service contracts may become tools. Authentication and arbitrary HTTP are never exposed. */
public final class SiigoToolRegistry {
    public static final Set<String> GROUPS=Set.of("products","customers","invoices","quotations","credit_notes","purchases","journals","vouchers","support_documents","payment_receipts","inventory_categories","catalogs","reports","webhooks");
    private final List<SyncToolSpecification> specifications=new ArrayList<>();
    private final Set<String> names=new HashSet<>();
    private final ToolSchemas schemas;
    private final ToolArguments arguments;
    private final ToolResults results;
    private final ServerConfiguration configuration;
    public SiigoToolRegistry(SiigoApi api,ServerConfiguration configuration) {
        this.configuration=configuration;
        if (!GROUPS.containsAll(configuration.toolGroups())) throw new IllegalArgumentException("Unknown SIIGO_MCP_TOOL_GROUPS entry");
        ObjectMapper mapper=JsonSupport.mapper(); schemas=new ToolSchemas(mapper); arguments=new ToolArguments(mapper); results=new ToolResults(mapper,configuration.secrets());
        add("products",ProductService.class,api.getProductService()); add("customers",CustomerService.class,api.getCustomerService());
        add("invoices",InvoiceService.class,api.getInvoiceService()); add("quotations",QuotationService.class,api.getQuotationService());
        add("credit_notes",CreditNoteService.class,api.getCreditNoteService()); add("purchases",PurchaseService.class,api.getPurchaseService());
        add("journals",JournalService.class,api.getJournalService()); add("vouchers",VoucherService.class,api.getVoucherService());
        add("support_documents",SupportDocumentService.class,api.getSupportDocumentService()); add("payment_receipts",PaymentReceiptService.class,api.getPaymentReceiptService());
        add("inventory_categories",InventoryCategoryService.class,api.getInventoryCategoryService()); add("catalogs",CatalogService.class,api.getCatalogService());
        add("reports",ReportService.class,api.getReportService()); add("webhooks",WebhookService.class,api.getWebhookService());
        specifications.sort(Comparator.comparing(spec -> spec.tool().name()));
    }
    public List<SyncToolSpecification> specifications() { return List.copyOf(specifications); }
    private void add(String group,Class<?> contract,Object service) {
        if (!configuration.toolGroups().isEmpty() && !configuration.toolGroups().contains(group)) return;
        Arrays.stream(contract.getDeclaredMethods()).filter(method -> !method.isDefault() && !method.isSynthetic())
            .sorted(Comparator.comparing(Method::getName)).forEach(method -> {
                boolean readOnly=method.getName().startsWith("get");
                if (configuration.readOnly() && !readOnly) return;
                String name="siigo_"+JsonSupport.snake(method.getName());
                if (!names.add(name)) throw new IllegalStateException("Duplicate MCP tool name");
                Tool tool=Tool.builder(name,schemas.forMethod(method)).title(method.getName()).description(description(group,method))
                    .annotations(ToolAnnotations.builder().readOnlyHint(readOnly).destructiveHint(!readOnly && !method.getName().startsWith("create"))
                        .idempotentHint(readOnly).openWorldHint(true).build()).build();
                specifications.add(SyncToolSpecification.builder().tool(tool).callHandler((exchange,request) -> {
                    try { return results.success(method.invoke(service,arguments.bind(method,request.arguments()))); }
                    catch (Exception failure) { return results.error(failure,readOnly); }
                }).build());
            });
    }
    private static String description(String group,Method method) {
        String text="Siigo Colombia / "+group+": "+JsonSupport.snake(method.getName()).replace('_',' ')+". Uses the configured company's Siigo account. ";
        if (method.getName().contains("ByDate")) text+="Inclusive accounting document dates (YYYY-MM-DD). Traverses ALL pages and applies the date filter locally; no unsupported remote date filter. ";
        else if (method.getName().startsWith("getAll")) text+="Traverses all pages and verifies completion. For large datasets prefer the paginated get operation. ";
        if (group.equals("journals")) text+="Siigo has ignored remote date_start/date_end and document_id filters in verified queries. Use get_journals_by_date for dates; do not assume remote document_id was applied. ";
        if (!method.getName().startsWith("get")) text+="Writes to Siigo: obtain user intent/approval in the MCP host before calling. Do not retry automatically after an uncertain outcome. ";
        return text+"Request uses Siigo JSON field names. Credentials and Partner-Id are supplied by the server, never as tool arguments.";
    }
}
