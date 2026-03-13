package com.crawlfilter;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.Registration;

public final class CrawlFilterExtension implements BurpExtension
{
    private CrawlFilterController controller;
    private Registration proxyRegistration;
    private Registration tabRegistration;

    @Override
    public void initialize(MontoyaApi api)
    {
        api.extension().setName("Crawl Filter");

        controller = new CrawlFilterController(api);
        proxyRegistration = api.proxy().registerRequestHandler(controller);
        tabRegistration = api.userInterface().registerSuiteTab("Crawl Filter", controller.uiComponent());

        api.extension().registerUnloadingHandler(() ->
        {
            if (proxyRegistration != null && proxyRegistration.isRegistered())
            {
                proxyRegistration.deregister();
            }

            if (tabRegistration != null && tabRegistration.isRegistered())
            {
                tabRegistration.deregister();
            }
        });

        api.logging().raiseInfoEvent("Crawl Filter loaded.");
        api.logging().logToOutput("Crawl Filter is capturing unique in-scope proxy requests.");
    }
}

