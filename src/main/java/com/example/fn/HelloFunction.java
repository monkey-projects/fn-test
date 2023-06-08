package com.example.fn;

public class HelloFunction {

    public static class Result {
	public String message;
	public String socket;
	public String format;
    }

    public Result handleRequest(String input) {
        String name = (input == null || input.isEmpty()) ? "world"  : input;

        System.out.println("Inside Java Hello World function");
	var res = new Result();
	res.message = "Hello, " + name + "!";
	res.socket = System.getenv("FN_LISTENER");
	res.format = System.getenv("FN_FORMAT");
	return res;
    }

}
