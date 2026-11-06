package vn.t3nexus.seller.domain.sellerprofile;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Thông tin liên hệ của gian hàng.
 */
public final class Contact {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final String personName;
    private final Phone phone;
    private final Address address;

    private Contact(String personName, Phone phone, Address address) {
        this.personName = personName;
        this.phone = phone;
        this.address = address;
    }

    public static Contact of(String personName, String phone, String address) {
        if (personName == null) {
            throw new SellerFieldException(SellerErrorCode.SELLER_FIELD_INVALID, "contact.personName");
        }
        String strippedName = personName.strip();
        String normalizedName = WHITESPACE.matcher(strippedName).replaceAll(" ");
        if (normalizedName.length() < 2 || normalizedName.length() > 100) {
            throw new SellerFieldException(SellerErrorCode.SELLER_FIELD_INVALID, "contact.personName");
        }

        Phone phoneVo = Phone.of(phone);

        Address addressVo = (address == null || address.isBlank())
                ? null
                : Address.of(address, "contact.address");

        return new Contact(normalizedName, phoneVo, addressVo);
    }

    public String personName() {
        return personName;
    }

    public Phone phone() {
        return phone;
    }

    public Address address() {
        return address;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Contact contact = (Contact) o;
        return Objects.equals(personName, contact.personName) &&
                Objects.equals(phone, contact.phone) &&
                Objects.equals(address, contact.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(personName, phone, address);
    }

    @Override
    public String toString() {
        return "Contact[***]";
    }
}
