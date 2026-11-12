package com.ankush.reposearch.domain;

public enum SortKey {
  stars("stars"),
  forks("forks"),
  updated("last_updated");

  private final String column;

  SortKey(String column) {
    this.column = column;
  }

  public String column() {
    return column;
  }
}
