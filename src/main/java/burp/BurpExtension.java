package burp;

import burp.api.montoya.MontoyaApi;
import com.pythonburp.BurpPythonIdeExtension;

public final class BurpExtension implements burp.api.montoya.BurpExtension {
    private final BurpPythonIdeExtension extension = new BurpPythonIdeExtension();

    @Override
    public void initialize(MontoyaApi api) {
        extension.initialize(api);
    }
}
