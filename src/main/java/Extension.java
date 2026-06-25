import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import com.crawlfilter.CrawlFilterExtension;

public final class Extension implements BurpExtension
{
    private final CrawlFilterExtension delegate = new CrawlFilterExtension();

    @Override
    public void initialize(MontoyaApi api)
    {
        delegate.initialize(api);
    }
}

