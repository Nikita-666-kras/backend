package com.blog.platform.integrations.channel;

import com.blog.platform.integrations.domain.OrderContext;

/**
 * Secondary notification after primary CRM write (best-effort; failures are logged, not thrown).
 */
public interface OrderNotifier {

    /** Lower runs first. CRM is handled separately and is always required. */
    int order();

    boolean enabled();

    void notify(OrderContext context);
}
