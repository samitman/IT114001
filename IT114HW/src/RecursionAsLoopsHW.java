//Samuel Itman
//IT114-001
//Professor Toegel
//9-29-20



public class RecursionAsLoopsHW {
	//The following code aims to sum up an integer with all of its preceding
	//integers that are greater than zero
	
	public static int sum(int x) {
		int sum = 0;
		
		if(x>=0) {
			do {
				sum += x;
				x--;
			}
			while(x>=0);		
		}
		return sum;
	}
	public static void main(String[] args) {
		System.out.println(sum(4));
	}
}
