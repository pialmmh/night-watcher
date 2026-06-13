package com.tb.nw.core;

import com.tb.nw.spi.api.PluginDescriptor;
import com.tb.nw.testkit.Fakes;

import java.util.List;

/** Same-package factory for core-root beans with package-private wiring. */
public final class TestCore {
    private TestCore() {}

    public static PluginRegistry pluginRegistry(List<PluginDescriptor> descriptors) {
        PluginRegistry r = new PluginRegistry();
        r.descriptors = Fakes.instanceOf(descriptors);
        r.init();
        return r;
    }
}
