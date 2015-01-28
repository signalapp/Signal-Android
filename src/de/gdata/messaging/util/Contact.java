package de.gdata.messaging.util;

import java.util.ArrayList;

/**
 * Created by jan on 26.01.15.
 */
public class Contact {
  public String id;
  public String name;
  public ArrayList<ContactEmail> emails;
  public ArrayList<ContactPhone> numbers;

  public Contact(String id, String name) {
    this.id = id;
    this.name = name;
    this.emails = new ArrayList<ContactEmail>();
    this.numbers = new ArrayList<ContactPhone>();
  }

  public void addEmail(String address, String type) {
    emails.add(new ContactEmail(address, type));
  }

  public void addNumber(String number, String type) {
    numbers.add(new ContactPhone(number, type));
  }
}
class ContactPhone {
  public String number;
  public String type;

  public ContactPhone(String number, String type) {
    this.number = number;
    this.type = type;
  }
}
class ContactEmail {
  public String address;
  public String type;

  public ContactEmail(String address, String type) {
    this.address = address;
    this.type = type;
  }
}