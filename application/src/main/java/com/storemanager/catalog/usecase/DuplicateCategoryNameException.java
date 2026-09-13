package com.storemanager.catalog.usecase;

/** Thrown when a Category is created with a name that already exists. */
public class DuplicateCategoryNameException extends RuntimeException {

    public DuplicateCategoryNameException(String name) {
        super("A category named '" + name + "' already exists");
    }
}
