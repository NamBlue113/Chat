package Client.model;

public class UserSearchResult {

    private String username;
    private String phone;

    public UserSearchResult(String username, String phone) {
        this.username = username;
        this.phone = phone;
    }

    public String getUsername() {
        return username;
    }

    public String getPhone() {
        return phone;
    }
}