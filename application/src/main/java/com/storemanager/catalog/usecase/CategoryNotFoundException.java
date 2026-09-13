package com.storemanager.catalog.usecase;

public class CategoryNotFoundException extends RuntimeException {

    public CategoryNotFoundException(Long categoryId) {
        super("Category with id " + categoryId + " does not exist");
    }
}
