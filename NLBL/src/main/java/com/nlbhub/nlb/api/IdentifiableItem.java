package com.nlbhub.nlb.api;

/**
 * The IdentifiableItem class
 *
 * @author Anton P. Kolosov
 * @version 1.0 1/15/14
 */
public interface IdentifiableItem {
    public String getId();

    public String getFullId();

    public boolean isDeleted();

    public IdentifiableItem getParent();

    public NonLinearBook getCurrentNLB();
}
