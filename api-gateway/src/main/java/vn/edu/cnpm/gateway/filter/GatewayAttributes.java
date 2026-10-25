package vn.edu.cnpm.gateway.filter;

public final class GatewayAttributes {
    public static final String CORRELATION_ID = GatewayAttributes.class.getName() + ".correlationId";
    public static final String ROUTE_POLICY = GatewayAttributes.class.getName() + ".routePolicy";
    public static final String USER_ID = GatewayAttributes.class.getName() + ".userId";
    public static final String USER_ROLE = GatewayAttributes.class.getName() + ".userRole";

    private GatewayAttributes() { }
}
