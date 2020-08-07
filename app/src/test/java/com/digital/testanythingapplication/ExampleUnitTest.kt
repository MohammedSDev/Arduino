package com.digital.testanythingapplication

import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
	@Test
	fun addition_isCorrect() {
//		assertEquals(4, 2 + 2)
	}
	@Test
	fun etst_forEach() {
		//var c = 0
//listOf(1,2,3,4,5).forEach ll@{
//	c = it
//	if(it == 2)
//		break@ll
//	c = 6
//}
//c


		fun foo() {
			listOf(1, 2, 3, 4, 5).forEach(fun(value: Int) {
				if (value == 3) return  // local return to the caller of the anonymous fun, i.e. the forEach loop
				print(value)
			})
			print(" done with anonymous function")
		}
		foo()

	}
}
