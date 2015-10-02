//Templated spread_sort library

//          Copyright Steven J. Ross 2001 - 2009.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE_1_0.txt or copy at
//          http://www.boost.org/LICENSE_1_0.txt)

//  See http://www.boost.org/ for updates, documentation, and revision history.
		  
/*
Some improvements suggested by:
Phil Endecott and Frank Gennari
Cygwin fix provided by:
Scott McMurray
*/

#ifndef BOOST_SPREAD_SORT_H
#define BOOST_SPREAD_SORT_H
#include <algorithm>
#include <cstring>
#include <vector>
#include "webrtc/system_wrappers/source/spreadsortlib/constants.hpp"

namespace boost {
  namespace detail {
  	//This only works on unsigned data types
  	template <typename T>
  	inline unsigned 
  	rough_log_2_size(const T& input) 
  	{
  		unsigned result = 0;
  		//The && is necessary on some compilers to avoid infinite loops; it doesn't significantly impair performance
  		while((input >> result) && (result < (8*sizeof(T)))) ++result;
  		return result;
  	}

  	//Gets the maximum size which we'll call spread_sort on to control worst-case performance
  	//Maintains both a minimum size to recurse and a check of distribution size versus count
  	//This is called for a set of bins, instead of bin-by-bin, to avoid performance overhead
  	inline size_t
  	get_max_count(unsigned log_range, size_t count)
  	{
  		unsigned divisor = rough_log_2_size(count);
  		//Making sure the divisor is positive
  		if(divisor > LOG_MEAN_BIN_SIZE)
  			divisor -= LOG_MEAN_BIN_SIZE;
  		else
  			divisor = 1;
  		unsigned relative_width = (LOG_CONST * log_range)/((divisor > MAX_SPLITS) ? MAX_SPLITS : divisor);
  		//Don't try to bitshift more than the size of an element
  		if((8*sizeof(size_t)) <= relative_width)
  			relative_width = (8*sizeof(size_t)) - 1;
  		return (size_t)1 << ((relative_width < (LOG_MEAN_BIN_SIZE + LOG_MIN_SPLIT_COUNT)) ? 
  			(LOG_MEAN_BIN_SIZE + LOG_MIN_SPLIT_COUNT) :  relative_width);
  	}

  	//Find the minimum and maximum using <
  	template <class RandomAccessIter>
  	inline void 
  	find_extremes(RandomAccessIter current, RandomAccessIter last, RandomAccessIter & max, RandomAccessIter & min)
  	{
  		min = max = current;
  		//Start from the second item, as max and min are initialized to the first
  		while(++current < last) {
  			if(*max < *current)
  				max = current;
  			else if(*current < *min)
  				min = current;
  		}
  	}

  	//Uses a user-defined comparison operator to find minimum and maximum
  	template <class RandomAccessIter, class compare>
  	inline void 
  	find_extremes(RandomAccessIter current, RandomAccessIter last, RandomAccessIter & max, RandomAccessIter & min, compare comp)
  	{
  		min = max = current;
  		while(++current < last) {
  			if(comp(*max, *current))
  				max = current;
  			else if(comp(*current, *min))
  				min = current;
  		}
  	}

  	//Gets a non-negative right bit shift to operate as a logarithmic divisor
  	inline int
  	get_log_divisor(size_t count, unsigned log_range)
  	{
  		int log_divisor;
  		//If we can finish in one iteration without exceeding either (2 to the MAX_SPLITS) or n bins, do so
  		if((log_divisor = log_range - rough_log_2_size(count)) <= 0 && log_range < MAX_SPLITS)
  			log_divisor = 0;
  		else {
  			//otherwise divide the data into an optimized number of pieces
  			log_divisor += LOG_MEAN_BIN_SIZE;
  			if(log_divisor < 0)
  				log_divisor = 0;
  			//Cannot exceed MAX_SPLITS or cache misses slow down bin lookups dramatically
  			if((log_range - log_divisor) > MAX_SPLITS)
  				log_divisor = log_range - MAX_SPLITS;
  		}
  		return log_divisor;
  	}

  	template <class RandomAccessIter>
  	inline RandomAccessIter * 
  	size_bins(std::vector<size_t> &bin_sizes, std::vector<RandomAccessIter> &bin_cache, unsigned cache_offset, unsigned &cache_end, unsigned bin_count)
  	{
  		//Assure space for the size of each bin, followed by initializing sizes
  		if(bin_count > bin_sizes.size())
  			bin_sizes.resize(bin_count);
  		for(size_t u = 0; u < bin_count; u++)
  			bin_sizes[u] = 0;
  		//Make sure there is space for the bins
  		cache_end = cache_offset + bin_count;
  		if(cache_end > bin_cache.size())
  			bin_cache.resize(cache_end);
  		return &(bin_cache[cache_offset]);
  	}

  	//Implementation for recursive integer sorting
  	template <class RandomAccessIter, class div_type, class data_type>
  	inline void 
  	spread_sort_rec(RandomAccessIter first, RandomAccessIter last, std::vector<RandomAccessIter> &bin_cache, unsigned cache_offset
  				  , std::vector<size_t> &bin_sizes)
  	{
  		//This step is roughly 10% of runtime, but it helps avoid worst-case behavior and improve behavior with real data
  		//If you know the maximum and minimum ahead of time, you can pass those values in and skip this step for the first iteration
  		RandomAccessIter max, min;
  		find_extremes(first, last, max, min);
  		//max and min will be the same (the first item) iff all values are equivalent
  		if(max == min)
  			return;
  		RandomAccessIter * target_bin;
  		unsigned log_divisor = get_log_divisor(last - first, rough_log_2_size((size_t)(*max >> 0) - (*min >> 0)));
  		div_type div_min = *min >> log_divisor;
  		div_type div_max = *max >> log_divisor;
  		unsigned bin_count = div_max - div_min + 1;
  		unsigned cache_end;
  		RandomAccessIter * bins = size_bins(bin_sizes, bin_cache, cache_offset, cache_end, bin_count);
  	
  		//Calculating the size of each bin; this takes roughly 10% of runtime
  		for (RandomAccessIter current = first; current != last;)
  			bin_sizes[(*(current++) >> log_divisor) - div_min]++;
  		//Assign the bin positions
  		bins[0] = first;
  		for(unsigned u = 0; u < bin_count - 1; u++)
  			bins[u + 1] = bins[u] + bin_sizes[u];
  
  		//Swap into place
  		//This dominates runtime, mostly in the swap and bin lookups
  		RandomAccessIter nextbinstart = first;
  		for(unsigned u = 0; u < bin_count - 1; ++u) {
  			RandomAccessIter * local_bin = bins + u;
  			nextbinstart += bin_sizes[u];
  			//Iterating over each element in this bin
  			for(RandomAccessIter current = *local_bin; current < nextbinstart; ++current) {
  				//Swapping elements in current into place until the correct element has been swapped in
  				for(target_bin = (bins + ((*current >> log_divisor) - div_min));  target_bin != local_bin; 
  					target_bin = bins + ((*current >> log_divisor) - div_min)) {
  					//3-way swap; this is about 1% faster than a 2-way swap with integers
  					//The main advantage is less copies are involved per item put in the correct place
  					data_type tmp;
  					RandomAccessIter b = (*target_bin)++;
  					RandomAccessIter * b_bin = bins + ((*b >> log_divisor) - div_min);
  					if (b_bin != local_bin) {
  						RandomAccessIter c = (*b_bin)++;
  						tmp = *c;
  						*c = *b;
  					} 
  					else
  						tmp = *b;
  					*b = *current;
  					*current = tmp;
  				}
  			}
  			*local_bin = nextbinstart;
  		}
  		bins[bin_count - 1] = last;
  
  		//If we've bucketsorted, the array is sorted and we should skip recursion
  		if(!log_divisor)
  			return;
  
  		//Recursing; log_divisor is the remaining range
  		size_t max_count = get_max_count(log_divisor, last - first);
  		RandomAccessIter lastPos = first;
  		for(unsigned u = cache_offset; u < cache_end; lastPos = bin_cache[u], ++u) {
  			size_t count = bin_cache[u] - lastPos;
  			//don't sort unless there are at least two items to compare
  			if(count < 2)
  				continue;
  			//using std::sort if its worst-case is better
  			if(count < max_count)
  				std::sort(lastPos, bin_cache[u]);
  			else
  				spread_sort_rec<RandomAccessIter, div_type, data_type>(lastPos, bin_cache[u], bin_cache, cache_end, bin_sizes);
  		}
  	}

  	//Generic bitshift-based 3-way swapping code
  	template <class RandomAccessIter, class div_type, class data_type, class right_shift>
  	inline void inner_swap_loop(RandomAccessIter * bins, const RandomAccessIter & nextbinstart, unsigned ii, right_shift &shift
  		, const unsigned log_divisor, const div_type div_min) 
  	{
  		RandomAccessIter * local_bin = bins + ii;
  		for(RandomAccessIter current = *local_bin; current < nextbinstart; ++current) {
  			for(RandomAccessIter * target_bin = (bins + (shift(*current, log_divisor) - div_min));  target_bin != local_bin; 
  				target_bin = bins + (shift(*current, log_divisor) - div_min)) {
  				data_type tmp;
  				RandomAccessIter b = (*target_bin)++;
  				RandomAccessIter * b_bin = bins + (shift(*b, log_divisor) - div_min);
  				//Three-way swap; if the item to be swapped doesn't belong in the current bin, swap it to where it belongs
  				if (b_bin != local_bin) {
  					RandomAccessIter c = (*b_bin)++;
  					tmp = *c;
  					*c = *b;
  				} 
  				//Note: we could increment current once the swap is done in this case, but that seems to impair performance
  				else
  					tmp = *b;
  				*b = *current;
  				*current = tmp;
  			}
  		}
  		*local_bin = nextbinstart;
  	}

  	//Standard swapping wrapper for ascending values
  	template <class RandomAccessIter, class div_type, class data_type, class right_shift>
  	inline void swap_loop(RandomAccessIter * bins, RandomAccessIter & nextbinstart, unsigned ii, right_shift &shift
  		, const std::vector<size_t> &bin_sizes, const unsigned log_divisor, const div_type div_min) 
  	{
  		nextbinstart += bin_sizes[ii];
  		inner_swap_loop<RandomAccessIter, div_type, data_type, right_shift>(bins, nextbinstart, ii, shift, log_divisor, div_min);
  	}

  	//Functor implementation for recursive sorting
  	template <class RandomAccessIter, class div_type, class data_type, class right_shift, class compare>
  	inline void 
  	spread_sort_rec(RandomAccessIter first, RandomAccessIter last, std::vector<RandomAccessIter> &bin_cache, unsigned cache_offset
  					, std::vector<size_t> &bin_sizes, right_shift shift, compare comp)
  	{
  		RandomAccessIter max, min;
  		find_extremes(first, last, max, min, comp);
  		if(max == min)
  			return;
  		unsigned log_divisor = get_log_divisor(last - first, rough_log_2_size((size_t)(shift(*max, 0)) - (shift(*min, 0))));
  		div_type div_min = shift(*min, log_divisor);
  		div_type div_max = shift(*max, log_divisor);
  		unsigned bin_count = div_max - div_min + 1;
  		unsigned cache_end;
  		RandomAccessIter * bins = size_bins(bin_sizes, bin_cache, cache_offset, cache_end, bin_count);
  			
  		//Calculating the size of each bin
  		for (RandomAccessIter current = first; current != last;)
  			bin_sizes[shift(*(current++), log_divisor) - div_min]++;
  		bins[0] = first;
  		for(unsigned u = 0; u < bin_count - 1; u++)
  			bins[u + 1] = bins[u] + bin_sizes[u];
  		
  		//Swap into place
  		RandomAccessIter nextbinstart = first;
  		for(unsigned u = 0; u < bin_count - 1; ++u)
  			swap_loop<RandomAccessIter, div_type, data_type, right_shift>(bins, nextbinstart, u, shift, bin_sizes, log_divisor, div_min);
  		bins[bin_count - 1] = last;
  		
  		//If we've bucketsorted, the array is sorted and we should skip recursion
  		if(!log_divisor)
  			return;
  		
  		//Recursing
  		size_t max_count = get_max_count(log_divisor, last - first);
  		RandomAccessIter lastPos = first;
  		for(unsigned u = cache_offset; u < cache_end; lastPos = bin_cache[u], ++u) {
  			size_t count = bin_cache[u] - lastPos;
  			if(count < 2)
  				continue;
  			if(count < max_count)
  				std::sort(lastPos, bin_cache[u], comp);
  			else
  				spread_sort_rec<RandomAccessIter, div_type, data_type, right_shift, compare>(lastPos, bin_cache[u], bin_cache, cache_end, bin_sizes, shift, comp);
  		}
  	}

  	//Functor implementation for recursive sorting with only Shift overridden
  	template <class RandomAccessIter, class div_type, class data_type, class right_shift>
  	inline void 
  	spread_sort_rec(RandomAccessIter first, RandomAccessIter last, std::vector<RandomAccessIter> &bin_cache, unsigned cache_offset
  					, std::vector<size_t> &bin_sizes, right_shift shift)
  	{
  		RandomAccessIter max, min;
  		find_extremes(first, last, max, min);
  		if(max == min)
  			return;
  		unsigned log_divisor = get_log_divisor(last - first, rough_log_2_size((size_t)(shift(*max, 0)) - (shift(*min, 0))));
  		div_type div_min = shift(*min, log_divisor);
  		div_type div_max = shift(*max, log_divisor);
  		unsigned bin_count = div_max - div_min + 1;
  		unsigned cache_end;
  		RandomAccessIter * bins = size_bins(bin_sizes, bin_cache, cache_offset, cache_end, bin_count);
  			
  		//Calculating the size of each bin
  		for (RandomAccessIter current = first; current != last;)
  			bin_sizes[shift(*(current++), log_divisor) - div_min]++;
  		bins[0] = first;
  		for(unsigned u = 0; u < bin_count - 1; u++)
  			bins[u + 1] = bins[u] + bin_sizes[u];
  		
  		//Swap into place
  		RandomAccessIter nextbinstart = first;
  		for(unsigned ii = 0; ii < bin_count - 1; ++ii)
  			swap_loop<RandomAccessIter, div_type, data_type, right_shift>(bins, nextbinstart, ii, shift, bin_sizes, log_divisor, div_min);
  		bins[bin_count - 1] = last;
  		
  		//If we've bucketsorted, the array is sorted and we should skip recursion
  		if(!log_divisor)
  			return;
  		
  		//Recursing
  		size_t max_count = get_max_count(log_divisor, last - first);
  		RandomAccessIter lastPos = first;
  		for(unsigned u = cache_offset; u < cache_end; lastPos = bin_cache[u], ++u) {
  			size_t count = bin_cache[u] - lastPos;
  			if(count < 2)
  				continue;
  			if(count < max_count)
  				std::sort(lastPos, bin_cache[u]);
  			else
  				spread_sort_rec<RandomAccessIter, div_type, data_type, right_shift>(lastPos, bin_cache[u], bin_cache, cache_end, bin_sizes, shift);
  		}
  	}

  	//Holds the bin vector and makes the initial recursive call
  	template <class RandomAccessIter, class div_type, class data_type>
  	inline void 
  	spread_sort(RandomAccessIter first, RandomAccessIter last, div_type, data_type)
  	{
  		std::vector<size_t> bin_sizes;
  		std::vector<RandomAccessIter> bin_cache;
  		spread_sort_rec<RandomAccessIter, div_type, data_type>(first, last, bin_cache, 0, bin_sizes);
  	}

  	template <class RandomAccessIter, class div_type, class data_type, class right_shift, class compare>
  	inline void 
  	spread_sort(RandomAccessIter first, RandomAccessIter last, div_type, data_type, right_shift shift, compare comp)
  	{
  		std::vector<size_t> bin_sizes;
  		std::vector<RandomAccessIter> bin_cache;
  		spread_sort_rec<RandomAccessIter, div_type, data_type, right_shift, compare>(first, last, bin_cache, 0, bin_sizes, shift, comp);
  	}

  	template <class RandomAccessIter, class div_type, class data_type, class right_shift>
  	inline void 
  	spread_sort(RandomAccessIter first, RandomAccessIter last, div_type, data_type, right_shift shift)
  	{
  		std::vector<size_t> bin_sizes;
  		std::vector<RandomAccessIter> bin_cache;
  		spread_sort_rec<RandomAccessIter, div_type, data_type, right_shift>(first, last, bin_cache, 0, bin_sizes, shift);
  	}
  }

  //Top-level sorting call for integers
  template <class RandomAccessIter>
  inline void integer_sort(RandomAccessIter first, RandomAccessIter last) 
  {
  	//Don't sort if it's too small to optimize
  	if(last - first < detail::MIN_SORT_SIZE)
  		std::sort(first, last);
  	else
  		detail::spread_sort(first, last, *first >> 0, *first);
  }

  //integer_sort with functors
  template <class RandomAccessIter, class right_shift, class compare>
  inline void integer_sort(RandomAccessIter first, RandomAccessIter last,
  						right_shift shift, compare comp) {
  	if(last - first < detail::MIN_SORT_SIZE)
  		std::sort(first, last, comp);
  	else
  		detail::spread_sort(first, last, shift(*first, 0), *first, shift, comp);
  }

  //integer_sort with right_shift functor
  template <class RandomAccessIter, class right_shift>
  inline void integer_sort(RandomAccessIter first, RandomAccessIter last,
  						right_shift shift) {
  	if(last - first < detail::MIN_SORT_SIZE)
  		std::sort(first, last);
  	else
  		detail::spread_sort(first, last, shift(*first, 0), *first, shift);
  }

  //------------------------------------------------------ float_sort source --------------------------------------
  //Casts a RandomAccessIter to the specified data type
  template<class cast_type, class RandomAccessIter>
  inline cast_type
  cast_float_iter(const RandomAccessIter & floatiter)
  {
  	cast_type result;
  	std::memcpy(&result, &(*floatiter), sizeof(cast_type));
  	return result;
  }

  //Casts a data element to the specified datinner_float_a type
  template<class data_type, class cast_type>
  inline cast_type
  mem_cast(const data_type & data)
  {
  	cast_type result;
  	std::memcpy(&result, &data, sizeof(cast_type));
  	return result;
  }

  namespace detail {
  	template <class RandomAccessIter, class div_type, class right_shift>
  	inline void 
  	find_extremes(RandomAccessIter current, RandomAccessIter last, div_type & max, div_type & min, right_shift shift)
  	{
  		min = max = shift(*current, 0);
  		while(++current < last) {
  			div_type value = shift(*current, 0);
  			if(max < value)
  				max = value;
  			else if(value < min)
  				min = value;
  		}
  	}

  	//Specialized swap loops for floating-point casting
  	template <class RandomAccessIter, class div_type, class data_type>
  	inline void inner_float_swap_loop(RandomAccessIter * bins, const RandomAccessIter & nextbinstart, unsigned ii
  		, const unsigned log_divisor, const div_type div_min) 
  	{
  		RandomAccessIter * local_bin = bins + ii;
  		for(RandomAccessIter current = *local_bin; current < nextbinstart; ++current) {
  			for(RandomAccessIter * target_bin = (bins + ((cast_float_iter<div_type, RandomAccessIter>(current) >> log_divisor) - div_min));  target_bin != local_bin; 
  				target_bin = bins + ((cast_float_iter<div_type, RandomAccessIter>(current) >> log_divisor) - div_min)) {
  				data_type tmp;
  				RandomAccessIter b = (*target_bin)++;
  				RandomAccessIter * b_bin = bins + ((cast_float_iter<div_type, RandomAccessIter>(b) >> log_divisor) - div_min);
  				//Three-way swap; if the item to be swapped doesn't belong in the current bin, swap it to where it belongs
  				if (b_bin != local_bin) {
  					RandomAccessIter c = (*b_bin)++;
  					tmp = *c;
  					*c = *b;
  				} 
  				else
  					tmp = *b;
  				*b = *current;
  				*current = tmp;
  			}
  		}
  		*local_bin = nextbinstart;
  	}

  	template <class RandomAccessIter, class div_type, class data_type>
  	inline void float_swap_loop(RandomAccessIter * bins, RandomAccessIter & nextbinstart, unsigned ii
  		, const std::vector<size_t> &bin_sizes, const unsigned log_divisor, const div_type div_min) 
  	{
  		nextbinstart += bin_sizes[ii];
  		inner_float_swap_loop<RandomAccessIter, div_type, data_type>(bins, nextbinstart, ii, log_divisor, div_min);
  	}

  	template <class RandomAccessIter, class cast_type>
  	inline void 
  	find_extremes(RandomAccessIter current, RandomAccessIter last, cast_type & max, cast_type & min)
  	{
  		min = max = cast_float_iter<cast_type, RandomAccessIter>(current);
  		while(++current < last) {
  			cast_type value = cast_float_iter<cast_type, RandomAccessIter>(current);
  			if(max < value)
  				max = value;
  			else if(value < min)
  				min = value;
  		}
  	}

  	//Special-case sorting of positive floats with casting instead of a right_shift
  	template <class RandomAccessIter, class div_type, class data_type>
  	inline void 
  	positive_float_sort_rec(RandomAccessIter first, RandomAccessIter last, std::vector<RandomAccessIter> &bin_cache, unsigned cache_offset
  					, std::vector<size_t> &bin_sizes)
  	{
  		div_type max, min;
  		find_extremes(first, last, max, min);
  		if(max == min)
  			return;
  		unsigned log_divisor = get_log_divisor(last - first, rough_log_2_size((size_t)(max) - min));
  		div_type div_min = min >> log_divisor;
  		div_type div_max = max >> log_divisor;
  		unsigned bin_count = div_max - div_min + 1;
  		unsigned cache_end;
  		RandomAccessIter * bins = size_bins(bin_sizes, bin_cache, cache_offset, cache_end, bin_count);
  			
  		//Calculating the size of each bin
  		for (RandomAccessIter current = first; current != last;)
  			bin_sizes[(cast_float_iter<div_type, RandomAccessIter>(current++) >> log_divisor) - div_min]++;
  		bins[0] = first;
  		for(unsigned u = 0; u < bin_count - 1; u++)
  			bins[u + 1] = bins[u] + bin_sizes[u];
  		
  		//Swap into place
  		RandomAccessIter nextbinstart = first;
  		for(unsigned u = 0; u < bin_count - 1; ++u)
  			float_swap_loop<RandomAccessIter, div_type, data_type>(bins, nextbinstart, u, bin_sizes, log_divisor, div_min);
  		bins[bin_count - 1] = last;
  		
  		//Return if we've completed bucketsorting
  		if(!log_divisor)
  			return;
  		
  		//Recursing
  		size_t max_count = get_max_count(log_divisor, last - first);
  		RandomAccessIter lastPos = first;
  		for(unsigned u = cache_offset; u < cache_end; lastPos = bin_cache[u], ++u) {
  			size_t count = bin_cache[u] - lastPos;
  			if(count < 2)
  				continue;
  			if(count < max_count)
  				std::sort(lastPos, bin_cache[u]);
  			else
  				positive_float_sort_rec<RandomAccessIter, div_type, data_type>(lastPos, bin_cache[u], bin_cache, cache_end, bin_sizes);
  		}
  	}

  	//Sorting negative_ float_s
  	//Note that bins are iterated in reverse order because max_neg_float = min_neg_int
  	template <class RandomAccessIter, class div_type, class data_type>
  	inline void 
  	negative_float_sort_rec(RandomAccessIter first, RandomAccessIter last, std::vector<RandomAccessIter> &bin_cache, unsigned cache_offset
  					, std::vector<size_t> &bin_sizes)
  	{
  		div_type max, min;
  		find_extremes(first, last, max, min);
  		if(max == min)
  			return;
  		unsigned log_divisor = get_log_divisor(last - first, rough_log_2_size((size_t)(max) - min));
  		div_type div_min = min >> log_divisor;
  		div_type div_max = max >> log_divisor;
  		unsigned bin_count = div_max - div_min + 1;
  		unsigned cache_end;
  		RandomAccessIter * bins = size_bins(bin_sizes, bin_cache, cache_offset, cache_end, bin_count);
  			
  		//Calculating the size of each bin
  		for (RandomAccessIter current = first; current != last;)
  			bin_sizes[(cast_float_iter<div_type, RandomAccessIter>(current++) >> log_divisor) - div_min]++;
  		bins[bin_count - 1] = first;
  		for(int ii = bin_count - 2; ii >= 0; --ii)
  			bins[ii] = bins[ii + 1] + bin_sizes[ii + 1];
  		
  		//Swap into place
  		RandomAccessIter nextbinstart = first;
  		//The last bin will always have the correct elements in it
  		for(int ii = bin_count - 1; ii > 0; --ii)
  			float_swap_loop<RandomAccessIter, div_type, data_type>(bins, nextbinstart, ii, bin_sizes, log_divisor, div_min);
  		//Since we don't process the last bin, we need to update its end position
  		bin_cache[cache_offset] = last;
  		
  		//Return if we've completed bucketsorting
  		if(!log_divisor)
  			return;
  		
  		//Recursing
  		size_t max_count = get_max_count(log_divisor, last - first);
  		RandomAccessIter lastPos = first;
  		for(int ii = cache_end - 1; ii >= (int)cache_offset; lastPos = bin_cache[ii], --ii) {
  			size_t count = bin_cache[ii] - lastPos;
  			if(count < 2)
  				continue;
  			if(count < max_count)
  				std::sort(lastPos, bin_cache[ii]);
  			else
  				negative_float_sort_rec<RandomAccessIter, div_type, data_type>(lastPos, bin_cache[ii], bin_cache, cache_end, bin_sizes);
  		}
  	}

  	//Sorting negative_ float_s
  	//Note that bins are iterated in reverse order because max_neg_float = min_neg_int
  	template <class RandomAccessIter, class div_type, class data_type, class right_shift>
  	inline void 
  	negative_float_sort_rec(RandomAccessIter first, RandomAccessIter last, std::vector<RandomAccessIter> &bin_cache, unsigned cache_offset
  					, std::vector<size_t> &bin_sizes, right_shift shift)
  	{
  		div_type max, min;
  		find_extremes(first, last, max, min, shift);
  		if(max == min)
  			return;
  		unsigned log_divisor = get_log_divisor(last - first, rough_log_2_size((size_t)(max) - min));
  		div_type div_min = min >> log_divisor;
  		div_type div_max = max >> log_divisor;
  		unsigned bin_count = div_max - div_min + 1;
  		unsigned cache_end;
  		RandomAccessIter * bins = size_bins(bin_sizes, bin_cache, cache_offset, cache_end, bin_count);
  			
  		//Calculating the size of each bin
  		for (RandomAccessIter current = first; current != last;)
  			bin_sizes[shift(*(current++), log_divisor) - div_min]++;
  		bins[bin_count - 1] = first;
  		for(int ii = bin_count - 2; ii >= 0; --ii)
  			bins[ii] = bins[ii + 1] + bin_sizes[ii + 1];
  		
  		//Swap into place
  		RandomAccessIter nextbinstart = first;
  		//The last bin will always have the correct elements in it
  		for(int ii = bin_count - 1; ii > 0; --ii)
  			swap_loop<RandomAccessIter, div_type, data_type, right_shift>(bins, nextbinstart, ii, shift, bin_sizes, log_divisor, div_min);
  		//Since we don't process the last bin, we need to update its end position
  		bin_cache[cache_offset] = last;
  		
  		//Return if we've completed bucketsorting
  		if(!log_divisor)
  			return;
  		
  		//Recursing
  		size_t max_count = get_max_count(log_divisor, last - first);
  		RandomAccessIter lastPos = first;
  		for(int ii = cache_end - 1; ii >= (int)cache_offset; lastPos = bin_cache[ii], --ii) {
  			size_t count = bin_cache[ii] - lastPos;
  			if(count < 2)
  				continue;
  			if(count < max_count)
  				std::sort(lastPos, bin_cache[ii]);
  			else
  				negative_float_sort_rec<RandomAccessIter, div_type, data_type, right_shift>(lastPos, bin_cache[ii], bin_cache, cache_end, bin_sizes, shift);
  		}
  	}

  	template <class RandomAccessIter, class div_type, class data_type, class right_shift, class compare>
  	inline void 
  	negative_float_sort_rec(RandomAccessIter first, RandomAccessIter last, std::vector<RandomAccessIter> &bin_cache, unsigned cache_offset
  					, std::vector<size_t> &bin_sizes, right_shift shift, compare comp)
  	{
  		div_type max, min;
  		find_extremes(first, last, max, min, shift);
  		if(max == min)
  			return;
  		unsigned log_divisor = get_log_divisor(last - first, rough_log_2_size((size_t)(max) - min));
  		div_type div_min = min >> log_divisor;
  		div_type div_max = max >> log_divisor;
  		unsigned bin_count = div_max - div_min + 1;
  		unsigned cache_end;
  		RandomAccessIter * bins = size_bins(bin_sizes, bin_cache, cache_offset, cache_end, bin_count);
  			
  		//Calculating the size of each bin
  		for (RandomAccessIter current = first; current != last;)
  			bin_sizes[shift(*(current++), log_divisor) - div_min]++;
  		bins[bin_count - 1] = first;
  		for(int ii = bin_count - 2; ii >= 0; --ii)
  			bins[ii] = bins[ii + 1] + bin_sizes[ii + 1];
  		
  		//Swap into place
  		RandomAccessIter nextbinstart = first;
  		//The last bin will always have the correct elements in it
  		for(int ii = bin_count - 1; ii > 0; --ii)
  			swap_loop<RandomAccessIter, div_type, data_type, right_shift>(bins, nextbinstart, ii, shift, bin_sizes, log_divisor, div_min);
  		//Since we don't process the last bin, we need to update its end position
  		bin_cache[cache_offset] = last;
  		
  		//Return if we've completed bucketsorting
  		if(!log_divisor)
  			return;
  		
  		//Recursing
  		size_t max_count = get_max_count(log_divisor, last - first);
  		RandomAccessIter lastPos = first;
  		for(int ii = cache_end - 1; ii >= (int)cache_offset; lastPos = bin_cache[ii], --ii) {
  			size_t count = bin_cache[ii] - lastPos;
  			if(count < 2)
  				continue;
  			if(count < max_count)
  				std::sort(lastPos, bin_cache[ii], comp);
  			else
  				negative_float_sort_rec<RandomAccessIter, div_type, data_type, right_shift, compare>(lastPos, bin_cache[ii], bin_cache, cache_end, bin_sizes, shift, comp);
  		}
  	}

  	//Casting special-case for floating-point sorting
  	template <class RandomAccessIter, class div_type, class data_type>
  	inline void 
  	float_sort_rec(RandomAccessIter first, RandomAccessIter last, std::vector<RandomAccessIter> &bin_cache, unsigned cache_offset
  					, std::vector<size_t> &bin_sizes)
  	{
  		div_type max, min;
  		find_extremes(first, last, max, min);
  		if(max == min)
  			return;
  		unsigned log_divisor = get_log_divisor(last - first, rough_log_2_size((size_t)(max) - min));
  		div_type div_min = min >> log_divisor;
  		div_type div_max = max >> log_divisor;
  		unsigned bin_count = div_max - div_min + 1;
  		unsigned cache_end;
  		RandomAccessIter * bins = size_bins(bin_sizes, bin_cache, cache_offset, cache_end, bin_count);
  			
  		//Calculating the size of each bin
  		for (RandomAccessIter current = first; current != last;)
  			bin_sizes[(cast_float_iter<div_type, RandomAccessIter>(current++) >> log_divisor) - div_min]++;
  		//The index of the first positive bin
  		div_type first_positive = (div_min < 0) ? -div_min : 0;
  		//Resetting if all bins are negative
  		if(cache_offset + first_positive > cache_end)
  			first_positive = cache_end - cache_offset;
  		//Reversing the order of the negative bins
  		//Note that because of the negative/positive ordering direction flip
  		//We can not depend upon bin order and positions matching up
  		//so bin_sizes must be reused to contain the end of the bin
  		if(first_positive > 0) {
  			bins[first_positive - 1] = first;
  			for(int ii = first_positive - 2; ii >= 0; --ii) {
  				bins[ii] = first + bin_sizes[ii + 1];
  				bin_sizes[ii] += bin_sizes[ii + 1];
  			}
  			//Handling positives following negatives
  			if((unsigned)first_positive < bin_count) {
  				bins[first_positive] = first + bin_sizes[0];
  				bin_sizes[first_positive] += bin_sizes[0];
  			}
  		}
  		else
  			bins[0] = first;
  		for(unsigned u = first_positive; u < bin_count - 1; u++) {
  			bins[u + 1] = first + bin_sizes[u];
  			bin_sizes[u + 1] += bin_sizes[u];
  		}
  		
  		//Swap into place
  		RandomAccessIter nextbinstart = first;
  		for(unsigned u = 0; u < bin_count; ++u) {
  			nextbinstart = first + bin_sizes[u];
  			inner_float_swap_loop<RandomAccessIter, div_type, data_type>(bins, nextbinstart, u, log_divisor, div_min);
  		}
  		
  		if(!log_divisor)
  			return;
  		
  		//Handling negative values first
  		size_t max_count = get_max_count(log_divisor, last - first);
  		RandomAccessIter lastPos = first;
  		for(int ii = cache_offset + first_positive - 1; ii >= (int)cache_offset ; lastPos = bin_cache[ii--]) {
  			size_t count = bin_cache[ii] - lastPos;
  			if(count < 2)
  				continue;
  			if(count < max_count)
  				std::sort(lastPos, bin_cache[ii]);
  			//sort negative values using reversed-bin spread_sort
  			else
  				negative_float_sort_rec<RandomAccessIter, div_type, data_type>(lastPos, bin_cache[ii], bin_cache, cache_end, bin_sizes);
  		}
  		
  		for(unsigned u = cache_offset + first_positive; u < cache_end; lastPos = bin_cache[u], ++u) {
  			size_t count = bin_cache[u] - lastPos;
  			if(count < 2)
  				continue;
  			if(count < max_count)
  				std::sort(lastPos, bin_cache[u]);
  			//sort positive values using normal spread_sort
  			else
  				positive_float_sort_rec<RandomAccessIter, div_type, data_type>(lastPos, bin_cache[u], bin_cache, cache_end, bin_sizes);
  		}
  	}

  	//Functor implementation for recursive sorting
  	template <class RandomAccessIter, class div_type, class data_type, class right_shift>
  	inline void 
  	float_sort_rec(RandomAccessIter first, RandomAccessIter last, std::vector<RandomAccessIter> &bin_cache, unsigned cache_offset
  					, std::vector<size_t> &bin_sizes, right_shift shift)
  	{
  		div_type max, min;
  		find_extremes(first, last, max, min, shift);
  		if(max == min)
  			return;
  		unsigned log_divisor = get_log_divisor(last - first, rough_log_2_size((size_t)(max) - min));
  		div_type div_min = min >> log_divisor;
  		div_type div_max = max >> log_divisor;
  		unsigned bin_count = div_max - div_min + 1;
  		unsigned cache_end;
  		RandomAccessIter * bins = size_bins(bin_sizes, bin_cache, cache_offset, cache_end, bin_count);
  			
  		//Calculating the size of each bin
  		for (RandomAccessIter current = first; current != last;)
  			bin_sizes[shift(*(current++), log_divisor) - div_min]++;
  		//The index of the first positive bin
  		div_type first_positive = (div_min < 0) ? -div_min : 0;
  		//Resetting if all bins are negative
  		if(cache_offset + first_positive > cache_end)
  			first_positive = cache_end - cache_offset;
  		//Reversing the order of the negative bins
  		//Note that because of the negative/positive ordering direction flip
  		//We can not depend upon bin order and positions matching up
  		//so bin_sizes must be reused to contain the end of the bin
  		if(first_positive > 0) {
  			bins[first_positive - 1] = first;
  			for(int ii = first_positive - 2; ii >= 0; --ii) {
  				bins[ii] = first + bin_sizes[ii + 1];
  				bin_sizes[ii] += bin_sizes[ii + 1];
  			}
  			//Handling positives following negatives
  			if((unsigned)first_positive < bin_count) {
  				bins[first_positive] = first + bin_sizes[0];
  				bin_sizes[first_positive] += bin_sizes[0];
  			}
  		}
  		else
  			bins[0] = first;
  		for(unsigned u = first_positive; u < bin_count - 1; u++) {
  			bins[u + 1] = first + bin_sizes[u];
  			bin_sizes[u + 1] += bin_sizes[u];
  		}
  		
  		//Swap into place
  		RandomAccessIter nextbinstart = first;
  		for(unsigned u = 0; u < bin_count; ++u) {
  			nextbinstart = first + bin_sizes[u];
  			inner_swap_loop<RandomAccessIter, div_type, data_type, right_shift>(bins, nextbinstart, u, shift, log_divisor, div_min);
  		}
  		
  		//Return if we've completed bucketsorting
  		if(!log_divisor)
  			return;
  		
  		//Handling negative values first
  		size_t max_count = get_max_count(log_divisor, last - first);
  		RandomAccessIter lastPos = first;
  		for(int ii = cache_offset + first_positive - 1; ii >= (int)cache_offset ; lastPos = bin_cache[ii--]) {
  			size_t count = bin_cache[ii] - lastPos;
  			if(count < 2)
  				continue;
  			if(count < max_count)
  				std::sort(lastPos, bin_cache[ii]);
  			//sort negative values using reversed-bin spread_sort
  			else
  				negative_float_sort_rec<RandomAccessIter, div_type, data_type, right_shift>(lastPos, bin_cache[ii], bin_cache, cache_end, bin_sizes, shift);
  		}
  		
  		for(unsigned u = cache_offset + first_positive; u < cache_end; lastPos = bin_cache[u], ++u) {
  			size_t count = bin_cache[u] - lastPos;
  			if(count < 2)
  				continue;
  			if(count < max_count)
  				std::sort(lastPos, bin_cache[u]);
  			//sort positive values using normal spread_sort
  			else
  				spread_sort_rec<RandomAccessIter, div_type, data_type, right_shift>(lastPos, bin_cache[u], bin_cache, cache_end, bin_sizes, shift);
  		}
  	}

  	template <class RandomAccessIter, class div_type, class data_type, class right_shift, class compare>
  	inline void 
  	float_sort_rec(RandomAccessIter first, RandomAccessIter last, std::vector<RandomAccessIter> &bin_cache, unsigned cache_offset
  					, std::vector<size_t> &bin_sizes, right_shift shift, compare comp)
  	{
  		div_type max, min;
  		find_extremes(first, last, max, min, shift);
  		if(max == min)
  			return;
  		unsigned log_divisor = get_log_divisor(last - first, rough_log_2_size((size_t)(max) - min));
  		div_type div_min = min >> log_divisor;
  		div_type div_max = max >> log_divisor;
  		unsigned bin_count = div_max - div_min + 1;
  		unsigned cache_end;
  		RandomAccessIter * bins = size_bins(bin_sizes, bin_cache, cache_offset, cache_end, bin_count);
  			
  		//Calculating the size of each bin
  		for (RandomAccessIter current = first; current != last;)
  			bin_sizes[shift(*(current++), log_divisor) - div_min]++;
  		//The index of the first positive bin
  		div_type first_positive = (div_min < 0) ? -div_min : 0;
  		//Resetting if all bins are negative
  		if(cache_offset + first_positive > cache_end)
  			first_positive = cache_end - cache_offset;
  		//Reversing the order of the negative bins
  		//Note that because of the negative/positive ordering direction flip
  		//We can not depend upon bin order and positions matching up
  		//so bin_sizes must be reused to contain the end of the bin
  		if(first_positive > 0) {
  			bins[first_positive - 1] = first;
  			for(int ii = first_positive - 2; ii >= 0; --ii) {
  				bins[ii] = first + bin_sizes[ii + 1];
  				bin_sizes[ii] += bin_sizes[ii + 1];
  			}
  			//Handling positives following negatives
  			if((unsigned)first_positive < bin_count) {
  				bins[first_positive] = first + bin_sizes[0];
  				bin_sizes[first_positive] += bin_sizes[0];
  			}
  		}
  		else
  			bins[0] = first;
  		for(unsigned u = first_positive; u < bin_count - 1; u++) {
  			bins[u + 1] = first + bin_sizes[u];
  			bin_sizes[u + 1] += bin_sizes[u];
  		}
  		
  		//Swap into place
  		RandomAccessIter nextbinstart = first;
  		for(unsigned u = 0; u < bin_count; ++u) {
  			nextbinstart = first + bin_sizes[u];
  			inner_swap_loop<RandomAccessIter, div_type, data_type, right_shift>(bins, nextbinstart, u, shift, log_divisor, div_min);
  		}
  		
  		//Return if we've completed bucketsorting
  		if(!log_divisor)
  			return;
  		
  		//Handling negative values first
  		size_t max_count = get_max_count(log_divisor, last - first);
  		RandomAccessIter lastPos = first;
  		for(int ii = cache_offset + first_positive - 1; ii >= (int)cache_offset ; lastPos = bin_cache[ii--]) {
  			size_t count = bin_cache[ii] - lastPos;
  			if(count < 2)
  				continue;
  			if(count < max_count)
  				std::sort(lastPos, bin_cache[ii]);
  			//sort negative values using reversed-bin spread_sort
  			else
  				negative_float_sort_rec<RandomAccessIter, div_type, data_type, right_shift>(lastPos, bin_cache[ii], bin_cache, cache_end, bin_sizes, shift, comp);
  		}
  		
  		for(unsigned u = cache_offset + first_positive; u < cache_end; lastPos = bin_cache[u], ++u) {
  			size_t count = bin_cache[u] - lastPos;
  			if(count < 2)
  				continue;
  			if(count < max_count)
  				std::sort(lastPos, bin_cache[u]);
  			//sort positive values using normal spread_sort
  			else
  				spread_sort_rec<RandomAccessIter, div_type, data_type, right_shift>(lastPos, bin_cache[u], bin_cache, cache_end, bin_sizes, shift, comp);
  		}
  	}

  	template <class RandomAccessIter, class cast_type, class data_type>
  	inline void 
  	float_Sort(RandomAccessIter first, RandomAccessIter last, cast_type, data_type)
  	{
  		std::vector<size_t> bin_sizes;
  		std::vector<RandomAccessIter> bin_cache;
  		float_sort_rec<RandomAccessIter, cast_type, data_type>(first, last, bin_cache, 0, bin_sizes);
  	}

  	template <class RandomAccessIter, class div_type, class data_type, class right_shift>
  	inline void 
  	float_Sort(RandomAccessIter first, RandomAccessIter last, div_type, data_type, right_shift shift)
  	{
  		std::vector<size_t> bin_sizes;
  		std::vector<RandomAccessIter> bin_cache;
  		float_sort_rec<RandomAccessIter, div_type, data_type, right_shift>(first, last, bin_cache, 0, bin_sizes, shift);
  	}

  	template <class RandomAccessIter, class div_type, class data_type, class right_shift, class compare>
  	inline void 
  	float_Sort(RandomAccessIter first, RandomAccessIter last, div_type, data_type, right_shift shift, compare comp)
  	{
  		std::vector<size_t> bin_sizes;
  		std::vector<RandomAccessIter> bin_cache;
  		float_sort_rec<RandomAccessIter, div_type, data_type, right_shift>(first, last, bin_cache, 0, bin_sizes, shift, comp);
  	}
  }

  //float_sort with casting
  //The cast_type must be equal in size to the data type, and must be a signed integer
  template <class RandomAccessIter, class cast_type>
  inline void float_sort_cast(RandomAccessIter first, RandomAccessIter last, cast_type cVal) 
  {
  	if(last - first < detail::MIN_SORT_SIZE)
  		std::sort(first, last);
  	else
  		detail::float_Sort(first, last, cVal, *first);
  }

  //float_sort with casting to an int
  //Only use this with IEEE floating-point numbers
  template <class RandomAccessIter>
  inline void float_sort_cast_to_int(RandomAccessIter first, RandomAccessIter last) 
  {
  	int cVal = 0;
  	float_sort_cast(first, last, cVal);
  }

  //float_sort with functors
  template <class RandomAccessIter, class right_shift>
  inline void float_sort(RandomAccessIter first, RandomAccessIter last, right_shift shift) 
  {
  	if(last - first < detail::MIN_SORT_SIZE)
  		std::sort(first, last);
  	else
  		detail::float_Sort(first, last, shift(*first, 0), *first, shift);
  }

  template <class RandomAccessIter, class right_shift, class compare>
  inline void float_sort(RandomAccessIter first, RandomAccessIter last, right_shift shift, compare comp) 
  {
  	if(last - first < detail::MIN_SORT_SIZE)
  		std::sort(first, last, comp);
  	else
  		detail::float_Sort(first, last, shift(*first, 0), *first, shift, comp);
  }

  //------------------------------------------------- string_sort source ---------------------------------------------
  namespace detail {
  	//Offsetting on identical characters.  This function works a character at a time for optimal worst-case performance.
  	template<class RandomAccessIter>
  	inline void
  	update_offset(RandomAccessIter first, RandomAccessIter finish, unsigned &char_offset)
  	{
  		unsigned nextOffset = char_offset;
  		bool done = false;
  		while(!done) {
  			RandomAccessIter curr = first;
  			do {
  				//ignore empties, but if the nextOffset would exceed the length or not match, exit; we've found the last matching character
  				if((*curr).size() > char_offset && ((*curr).size() <= (nextOffset + 1) || (*curr)[nextOffset] != (*first)[nextOffset])) {
  					done = true;
  					break;
  				}
  			} while(++curr != finish);
  			if(!done)
  				++nextOffset;
  		} 
  		char_offset = nextOffset;
  	}

  	//Offsetting on identical characters.  This function works a character at a time for optimal worst-case performance.
  	template<class RandomAccessIter, class get_char, class get_length>
  	inline void
  	update_offset(RandomAccessIter first, RandomAccessIter finish, unsigned &char_offset, get_char getchar, get_length length)
  	{
  		unsigned nextOffset = char_offset;
  		bool done = false;
  		while(!done) {
  			RandomAccessIter curr = first;
  			do {
  				//ignore empties, but if the nextOffset would exceed the length or not match, exit; we've found the last matching character
  				if(length(*curr) > char_offset && (length(*curr) <= (nextOffset + 1) || getchar((*curr), nextOffset) != getchar((*first), nextOffset))) {
  					done = true;
  					break;
  				}
  			} while(++curr != finish);
  			if(!done)
  				++nextOffset;
  		} 
  		char_offset = nextOffset;
  	}

  	//A comparison functor for strings that assumes they are identical up to char_offset
  	template<class data_type, class unsignedchar_type>
  	struct offset_lessthan {
  		offset_lessthan(unsigned char_offset) : fchar_offset(char_offset){}
  		inline bool operator()(const data_type &x, const data_type &y) const 
  		{
  			unsigned minSize = std::min(x.size(), y.size());
  			for(unsigned u = fchar_offset; u < minSize; ++u) {
  				if(static_cast<unsignedchar_type>(x[u]) < static_cast<unsignedchar_type>(y[u]))
  					return true;
  				else if(static_cast<unsignedchar_type>(y[u]) < static_cast<unsignedchar_type>(x[u]))
  					return false;
  			}
  			return x.size() < y.size();
  		}
  		unsigned fchar_offset;
  	};

  	//A comparison functor for strings that assumes they are identical up to char_offset
  	template<class data_type, class unsignedchar_type>
  	struct offset_greaterthan {
  		offset_greaterthan(unsigned char_offset) : fchar_offset(char_offset){}
  		inline bool operator()(const data_type &x, const data_type &y) const 
  		{
  			unsigned minSize = std::min(x.size(), y.size());
  			for(unsigned u = fchar_offset; u < minSize; ++u) {
  				if(static_cast<unsignedchar_type>(x[u]) > static_cast<unsignedchar_type>(y[u]))
  					return true;
  				else if(static_cast<unsignedchar_type>(y[u]) > static_cast<unsignedchar_type>(x[u]))
  					return false;
  			}
  			return x.size() > y.size();
  		}
  		unsigned fchar_offset;
  	};

  	//A comparison functor for strings that assumes they are identical up to char_offset
  	template<class data_type, class get_char, class get_length>
  	struct offset_char_lessthan {
  		offset_char_lessthan(unsigned char_offset) : fchar_offset(char_offset){}
  		inline bool operator()(const data_type &x, const data_type &y) const 
  		{
  			unsigned minSize = std::min(length(x), length(y));
  			for(unsigned u = fchar_offset; u < minSize; ++u) {
  				if(getchar(x, u) < getchar(y, u))
  					return true;
  				else if(getchar(y, u) < getchar(x, u))
  					return false;
  			}
  			return length(x) < length(y);
  		}
  		unsigned fchar_offset;
  		get_char getchar;
  		get_length length;
  	};

  	//String sorting recursive implementation
  	template <class RandomAccessIter, class data_type, class unsignedchar_type>
  	inline void 
  	string_sort_rec(RandomAccessIter first, RandomAccessIter last, unsigned char_offset, std::vector<RandomAccessIter> &bin_cache
  		, unsigned cache_offset, std::vector<size_t> &bin_sizes)
  	{
  		//This section is not strictly necessary, but makes handling of long identical substrings much faster, with a mild average performance impact.
  		//Iterate to the end of the empties.  If all empty, return
  		while((*first).size() <= char_offset) {
  			if(++first == last)
  				return;
  		}
  		RandomAccessIter finish = last - 1;
  		//Getting the last non-empty
  		for(;(*finish).size() <= char_offset; --finish) { }
  		++finish;
  		//Offsetting on identical characters.  This section works a character at a time for optimal worst-case performance.
  		update_offset(first, finish, char_offset);
  		
  		const unsigned bin_count = (1 << (sizeof(unsignedchar_type)*8));
  		//Equal worst-case between radix and comparison-based is when bin_count = n*log(n).
  		const unsigned max_size = bin_count;
  		const unsigned membin_count = bin_count + 1;
  		unsigned cache_end;
  		RandomAccessIter * bins = size_bins(bin_sizes, bin_cache, cache_offset, cache_end, membin_count) + 1;
  			
  		//Calculating the size of each bin; this takes roughly 10% of runtime
  		for (RandomAccessIter current = first; current != last; ++current) {
  			if((*current).size() <= char_offset) {
  				bin_sizes[0]++;
  			}
  			else
  				bin_sizes[static_cast<unsignedchar_type>((*current)[char_offset]) + 1]++;
  		}
  		//Assign the bin positions
  		bin_cache[cache_offset] = first;
  		for(unsigned u = 0; u < membin_count - 1; u++)
  			bin_cache[cache_offset + u + 1] = bin_cache[cache_offset + u] + bin_sizes[u];
  		
  		//Swap into place
  		RandomAccessIter nextbinstart = first;
  		//handling empty bins
  		RandomAccessIter * local_bin = &(bin_cache[cache_offset]);
  		nextbinstart +=	bin_sizes[0];
  		RandomAccessIter * target_bin;
  		//Iterating over each element in the bin of empties
  		for(RandomAccessIter current = *local_bin; current < nextbinstart; ++current) {
  			//empties belong in this bin
  			while((*current).size() > char_offset) {
  				target_bin = bins + static_cast<unsignedchar_type>((*current)[char_offset]);
  				iter_swap(current, (*target_bin)++);
  			}
  		}
  		*local_bin = nextbinstart;
  		//iterate backwards to find the last bin with elements in it; this saves iterations in multiple loops
  		unsigned last_bin = bin_count - 1;
  		for(; last_bin && !bin_sizes[last_bin + 1]; --last_bin) { }
  		//This dominates runtime, mostly in the swap and bin lookups
  		for(unsigned u = 0; u < last_bin; ++u) {
  			local_bin = bins + u;
  			nextbinstart += bin_sizes[u + 1];
  			//Iterating over each element in this bin
  			for(RandomAccessIter current = *local_bin; current < nextbinstart; ++current) {
  				//Swapping elements in current into place until the correct element has been swapped in
  				for(target_bin = bins + static_cast<unsignedchar_type>((*current)[char_offset]);  target_bin != local_bin; 
  					target_bin = bins + static_cast<unsignedchar_type>((*current)[char_offset]))
  					iter_swap(current, (*target_bin)++);
  			}
  			*local_bin = nextbinstart;
  		}
  		bins[last_bin] = last;
  		//Recursing
  		RandomAccessIter lastPos = bin_cache[cache_offset];
  		//Skip this loop for empties
  		for(unsigned u = cache_offset + 1; u < cache_offset + last_bin + 2; lastPos = bin_cache[u], ++u) {
  			size_t count = bin_cache[u] - lastPos;
  			//don't sort unless there are at least two items to compare
  			if(count < 2)
  				continue;
  			//using std::sort if its worst-case is better
  			if(count < max_size)
  				std::sort(lastPos, bin_cache[u], offset_lessthan<data_type, unsignedchar_type>(char_offset + 1));
  			else
  				string_sort_rec<RandomAccessIter, data_type, unsignedchar_type>(lastPos, bin_cache[u], char_offset + 1, bin_cache, cache_end, bin_sizes);
  		}
  	}

  	//Sorts strings in reverse order, with empties at the end
  	template <class RandomAccessIter, class data_type, class unsignedchar_type>
  	inline void 
  	reverse_string_sort_rec(RandomAccessIter first, RandomAccessIter last, unsigned char_offset, std::vector<RandomAccessIter> &bin_cache
  		, unsigned cache_offset, std::vector<size_t> &bin_sizes)
  	{
  		//This section is not strictly necessary, but makes handling of long identical substrings much faster, with a mild average performance impact.
  		RandomAccessIter curr = first;
  		//Iterate to the end of the empties.  If all empty, return
  		while((*curr).size() <= char_offset) {
  			if(++curr == last)
  				return;
  		}
  		//Getting the last non-empty
  		while((*(--last)).size() <= char_offset) { }
  		++last;
  		//Offsetting on identical characters.  This section works a character at a time for optimal worst-case performance.
  		update_offset(curr, last, char_offset);
  		RandomAccessIter * target_bin;
  		
  		const unsigned bin_count = (1 << (sizeof(unsignedchar_type)*8));
  		//Equal worst-case between radix and comparison-based is when bin_count = n*log(n).
  		const unsigned max_size = bin_count;
  		const unsigned membin_count = bin_count + 1;
  		const unsigned max_bin = bin_count - 1;
  		unsigned cache_end;
  		RandomAccessIter * bins = size_bins(bin_sizes, bin_cache, cache_offset, cache_end, membin_count);
  		RandomAccessIter * end_bin = &(bin_cache[cache_offset + max_bin]);
  			
  		//Calculating the size of each bin; this takes roughly 10% of runtime
  		for (RandomAccessIter current = first; current != last; ++current) {
  			if((*current).size() <= char_offset) {
  				bin_sizes[bin_count]++;
  			}
  			else
  				bin_sizes[max_bin - static_cast<unsignedchar_type>((*current)[char_offset])]++;
  		}
  		//Assign the bin positions
  		bin_cache[cache_offset] = first;
  		for(unsigned u = 0; u < membin_count - 1; u++)
  			bin_cache[cache_offset + u + 1] = bin_cache[cache_offset + u] + bin_sizes[u];
  		
  		//Swap into place
  		RandomAccessIter nextbinstart = last;
  		//handling empty bins
  		RandomAccessIter * local_bin = &(bin_cache[cache_offset + bin_count]);
  		RandomAccessIter lastFull = *local_bin;
  		//Iterating over each element in the bin of empties
  		for(RandomAccessIter current = *local_bin; current < nextbinstart; ++current) {
  			//empties belong in this bin
  			while((*current).size() > char_offset) {
  				target_bin = end_bin - static_cast<unsignedchar_type>((*current)[char_offset]);
  				iter_swap(current, (*target_bin)++);
  			}
  		}
  		*local_bin = nextbinstart;
  		nextbinstart = first;
  		//iterate backwards to find the last bin with elements in it; this saves iterations in multiple loops
  		unsigned last_bin = max_bin;
  		for(; last_bin && !bin_sizes[last_bin]; --last_bin) { }
  		//This dominates runtime, mostly in the swap and bin lookups
  		for(unsigned u = 0; u < last_bin; ++u) {
  			local_bin = bins + u;
  			nextbinstart += bin_sizes[u];
  			//Iterating over each element in this bin
  			for(RandomAccessIter current = *local_bin; current < nextbinstart; ++current) {
  				//Swapping elements in current into place until the correct element has been swapped in
  				for(target_bin = end_bin - static_cast<unsignedchar_type>((*current)[char_offset]);  target_bin != local_bin; 
  					target_bin = end_bin - static_cast<unsignedchar_type>((*current)[char_offset]))
  					iter_swap(current, (*target_bin)++);
  			}
  			*local_bin = nextbinstart;
  		}
  		bins[last_bin] = lastFull;
  		//Recursing
  		RandomAccessIter lastPos = first;
  		//Skip this loop for empties
  		for(unsigned u = cache_offset; u <= cache_offset + last_bin; lastPos = bin_cache[u], ++u) {
  			size_t count = bin_cache[u] - lastPos;
  			//don't sort unless there are at least two items to compare
  			if(count < 2)
  				continue;
  			//using std::sort if its worst-case is better
  			if(count < max_size)
  				std::sort(lastPos, bin_cache[u], offset_greaterthan<data_type, unsignedchar_type>(char_offset + 1));
  			else
  				reverse_string_sort_rec<RandomAccessIter, data_type, unsignedchar_type>(lastPos, bin_cache[u], char_offset + 1, bin_cache, cache_end, bin_sizes);
  		}
  	}

  	//String sorting recursive implementation
  	template <class RandomAccessIter, class data_type, class unsignedchar_type, class get_char, class get_length>
  	inline void 
  	string_sort_rec(RandomAccessIter first, RandomAccessIter last, unsigned char_offset, std::vector<RandomAccessIter> &bin_cache
  		, unsigned cache_offset, std::vector<size_t> &bin_sizes, get_char getchar, get_length length)
  	{
  		//This section is not strictly necessary, but makes handling of long identical substrings much faster, with a mild average performance impact.
  		//Iterate to the end of the empties.  If all empty, return
  		while(length(*first) <= char_offset) {
  			if(++first == last)
  				return;
  		}
  		RandomAccessIter finish = last - 1;
  		//Getting the last non-empty
  		for(;length(*finish) <= char_offset; --finish) { }
  		++finish;
  		update_offset(first, finish, char_offset, getchar, length);
  		
  		const unsigned bin_count = (1 << (sizeof(unsignedchar_type)*8));
  		//Equal worst-case between radix and comparison-based is when bin_count = n*log(n).
  		const unsigned max_size = bin_count;
  		const unsigned membin_count = bin_count + 1;
  		unsigned cache_end;
  		RandomAccessIter * bins = size_bins(bin_sizes, bin_cache, cache_offset, cache_end, membin_count) + 1;
  			
  		//Calculating the size of each bin; this takes roughly 10% of runtime
  		for (RandomAccessIter current = first; current != last; ++current) {
  			if(length(*current) <= char_offset) {
  				bin_sizes[0]++;
  			}
  			else
  				bin_sizes[getchar((*current), char_offset) + 1]++;
  		}
  		//Assign the bin positions
  		bin_cache[cache_offset] = first;
  		for(unsigned u = 0; u < membin_count - 1; u++)
  			bin_cache[cache_offset + u + 1] = bin_cache[cache_offset + u] + bin_sizes[u];
  		
  		//Swap into place
  		RandomAccessIter nextbinstart = first;
  		//handling empty bins
  		RandomAccessIter * local_bin = &(bin_cache[cache_offset]);
  		nextbinstart +=	bin_sizes[0];
  		RandomAccessIter * target_bin;
  		//Iterating over each element in the bin of empties
  		for(RandomAccessIter current = *local_bin; current < nextbinstart; ++current) {
  			//empties belong in this bin
  			while(length(*current) > char_offset) {
  				target_bin = bins + getchar((*current), char_offset);
  				iter_swap(current, (*target_bin)++);
  			}
  		}
  		*local_bin = nextbinstart;
  		//iterate backwards to find the last bin with elements in it; this saves iterations in multiple loops
  		unsigned last_bin = bin_count - 1;
  		for(; last_bin && !bin_sizes[last_bin + 1]; --last_bin) { }
  		//This dominates runtime, mostly in the swap and bin lookups
  		for(unsigned ii = 0; ii < last_bin; ++ii) {
  			local_bin = bins + ii;
  			nextbinstart += bin_sizes[ii + 1];
  			//Iterating over each element in this bin
  			for(RandomAccessIter current = *local_bin; current < nextbinstart; ++current) {
  				//Swapping elements in current into place until the correct element has been swapped in
  				for(target_bin = bins + getchar((*current), char_offset);  target_bin != local_bin; 
  					target_bin = bins + getchar((*current), char_offset))
  					iter_swap(current, (*target_bin)++);
  			}
  			*local_bin = nextbinstart;
  		}
  		bins[last_bin] = last;
  		
  		//Recursing
  		RandomAccessIter lastPos = bin_cache[cache_offset];
  		//Skip this loop for empties
  		for(unsigned u = cache_offset + 1; u < cache_offset + last_bin + 2; lastPos = bin_cache[u], ++u) {
  			size_t count = bin_cache[u] - lastPos;
  			//don't sort unless there are at least two items to compare
  			if(count < 2)
  				continue;
  			//using std::sort if its worst-case is better
  			if(count < max_size)
  				std::sort(lastPos, bin_cache[u], offset_char_lessthan<data_type, get_char, get_length>(char_offset + 1));
  			else
  				string_sort_rec<RandomAccessIter, data_type, unsignedchar_type, get_char, get_length>(lastPos, bin_cache[u], char_offset + 1, bin_cache, cache_end, bin_sizes, getchar, length);
  		}
  	}

  	//String sorting recursive implementation
  	template <class RandomAccessIter, class data_type, class unsignedchar_type, class get_char, class get_length, class compare>
  	inline void 
  	string_sort_rec(RandomAccessIter first, RandomAccessIter last, unsigned char_offset, std::vector<RandomAccessIter> &bin_cache
  		, unsigned cache_offset, std::vector<size_t> &bin_sizes, get_char getchar, get_length length, compare comp)
  	{
  		//This section is not strictly necessary, but makes handling of long identical substrings much faster, with a mild average performance impact.
  		//Iterate to the end of the empties.  If all empty, return
  		while(length(*first) <= char_offset) {
  			if(++first == last)
  				return;
  		}
  		RandomAccessIter finish = last - 1;
  		//Getting the last non-empty
  		for(;length(*finish) <= char_offset; --finish) { }
  		++finish;
  		update_offset(first, finish, char_offset, getchar, length);
  		
  		const unsigned bin_count = (1 << (sizeof(unsignedchar_type)*8));
  		//Equal worst-case between radix and comparison-based is when bin_count = n*log(n).
  		const unsigned max_size = bin_count;
  		const unsigned membin_count = bin_count + 1;
  		unsigned cache_end;
  		RandomAccessIter * bins = size_bins(bin_sizes, bin_cache, cache_offset, cache_end, membin_count) + 1;
  			
  		//Calculating the size of each bin; this takes roughly 10% of runtime
  		for (RandomAccessIter current = first; current != last; ++current) {
  			if(length(*current) <= char_offset) {
  				bin_sizes[0]++;
  			}
  			else
  				bin_sizes[getchar((*current), char_offset) + 1]++;
  		}
  		//Assign the bin positions
  		bin_cache[cache_offset] = first;
  		for(unsigned u = 0; u < membin_count - 1; u++)
  			bin_cache[cache_offset + u + 1] = bin_cache[cache_offset + u] + bin_sizes[u];
  		
  		//Swap into place
  		RandomAccessIter nextbinstart = first;
  		//handling empty bins
  		RandomAccessIter * local_bin = &(bin_cache[cache_offset]);
  		nextbinstart +=	bin_sizes[0];
  		RandomAccessIter * target_bin;
  		//Iterating over each element in the bin of empties
  		for(RandomAccessIter current = *local_bin; current < nextbinstart; ++current) {
  			//empties belong in this bin
  			while(length(*current) > char_offset) {
  				target_bin = bins + getchar((*current), char_offset);
  				iter_swap(current, (*target_bin)++);
  			}
  		}
  		*local_bin = nextbinstart;
  		//iterate backwards to find the last bin with elements in it; this saves iterations in multiple loops
  		unsigned last_bin = bin_count - 1;
  		for(; last_bin && !bin_sizes[last_bin + 1]; --last_bin) { }
  		//This dominates runtime, mostly in the swap and bin lookups
  		for(unsigned u = 0; u < last_bin; ++u) {
  			local_bin = bins + u;
  			nextbinstart += bin_sizes[u + 1];
  			//Iterating over each element in this bin
  			for(RandomAccessIter current = *local_bin; current < nextbinstart; ++current) {
  				//Swapping elements in current into place until the correct element has been swapped in
  				for(target_bin = bins + getchar((*current), char_offset);  target_bin != local_bin; 
  					target_bin = bins + getchar((*current), char_offset))
  					iter_swap(current, (*target_bin)++);
  			}
  			*local_bin = nextbinstart;
  		}
  		bins[last_bin] = last;
  		
  		//Recursing
  		RandomAccessIter lastPos = bin_cache[cache_offset];
  		//Skip this loop for empties
  		for(unsigned u = cache_offset + 1; u < cache_offset + last_bin + 2; lastPos = bin_cache[u], ++u) {
  			size_t count = bin_cache[u] - lastPos;
  			//don't sort unless there are at least two items to compare
  			if(count < 2)
  				continue;
  			//using std::sort if its worst-case is better
  			if(count < max_size)
  				std::sort(lastPos, bin_cache[u], comp);
  			else
  				string_sort_rec<RandomAccessIter, data_type, unsignedchar_type, get_char, get_length, compare>(lastPos
  					, bin_cache[u], char_offset + 1, bin_cache, cache_end, bin_sizes, getchar, length, comp);
  		}
  	}

  	//Sorts strings in reverse order, with empties at the end
  	template <class RandomAccessIter, class data_type, class unsignedchar_type, class get_char, class get_length, class compare>
  	inline void 
  	reverse_string_sort_rec(RandomAccessIter first, RandomAccessIter last, unsigned char_offset, std::vector<RandomAccessIter> &bin_cache
  		, unsigned cache_offset, std::vector<size_t> &bin_sizes, get_char getchar, get_length length, compare comp)
  	{
  		//This section is not strictly necessary, but makes handling of long identical substrings much faster, with a mild average performance impact.
  		RandomAccessIter curr = first;
  		//Iterate to the end of the empties.  If all empty, return
  		while(length(*curr) <= char_offset) {
  			if(++curr == last)
  				return;
  		}
  		//Getting the last non-empty
  		while(length(*(--last)) <= char_offset) { }
  		++last;
  		//Offsetting on identical characters.  This section works a character at a time for optimal worst-case performance.
  		update_offset(first, last, char_offset, getchar, length);
  		
  		const unsigned bin_count = (1 << (sizeof(unsignedchar_type)*8));
  		//Equal worst-case between radix and comparison-based is when bin_count = n*log(n).
  		const unsigned max_size = bin_count;
  		const unsigned membin_count = bin_count + 1;
  		const unsigned max_bin = bin_count - 1;
  		unsigned cache_end;
  		RandomAccessIter * bins = size_bins(bin_sizes, bin_cache, cache_offset, cache_end, membin_count);
  		RandomAccessIter *end_bin = &(bin_cache[cache_offset + max_bin]);
  			
  		//Calculating the size of each bin; this takes roughly 10% of runtime
  		for (RandomAccessIter current = first; current != last; ++current) {
  			if(length(*current) <= char_offset) {
  				bin_sizes[bin_count]++;
  			}
  			else
  				bin_sizes[max_bin - getchar((*current), char_offset)]++;
  		}
  		//Assign the bin positions
  		bin_cache[cache_offset] = first;
  		for(unsigned u = 0; u < membin_count - 1; u++)
  			bin_cache[cache_offset + u + 1] = bin_cache[cache_offset + u] + bin_sizes[u];
  		
  		//Swap into place
  		RandomAccessIter nextbinstart = last;
  		//handling empty bins
  		RandomAccessIter * local_bin = &(bin_cache[cache_offset + bin_count]);
  		RandomAccessIter lastFull = *local_bin;
  		RandomAccessIter * target_bin;
  		//Iterating over each element in the bin of empties
  		for(RandomAccessIter current = *local_bin; current < nextbinstart; ++current) {
  			//empties belong in this bin
  			while(length(*current) > char_offset) {
  				target_bin = end_bin - getchar((*current), char_offset);
  				iter_swap(current, (*target_bin)++);
  			}
  		}
  		*local_bin = nextbinstart;
  		nextbinstart = first;
  		//iterate backwards to find the last bin with elements in it; this saves iterations in multiple loops
  		unsigned last_bin = max_bin;
  		for(; last_bin && !bin_sizes[last_bin]; --last_bin) { }
  		//This dominates runtime, mostly in the swap and bin lookups
  		for(unsigned u = 0; u < last_bin; ++u) {
  			local_bin = bins + u;
  			nextbinstart += bin_sizes[u];
  			//Iterating over each element in this bin
  			for(RandomAccessIter current = *local_bin; current < nextbinstart; ++current) {
  				//Swapping elements in current into place until the correct element has been swapped in
  				for(target_bin = end_bin - getchar((*current), char_offset);  target_bin != local_bin; 
  					target_bin = end_bin - getchar((*current), char_offset))
  					iter_swap(current, (*target_bin)++);
  			}
  			*local_bin = nextbinstart;
  		}
  		bins[last_bin] = lastFull;
  		//Recursing
  		RandomAccessIter lastPos = first;
  		//Skip this loop for empties
  		for(unsigned u = cache_offset; u <= cache_offset + last_bin; lastPos = bin_cache[u], ++u) {
  			size_t count = bin_cache[u] - lastPos;
  			//don't sort unless there are at least two items to compare
  			if(count < 2)
  				continue;
  			//using std::sort if its worst-case is better
  			if(count < max_size)
  				std::sort(lastPos, bin_cache[u], comp);
  			else
  				reverse_string_sort_rec<RandomAccessIter, data_type, unsignedchar_type, get_char, get_length, compare>(lastPos
  					, bin_cache[u], char_offset + 1, bin_cache, cache_end, bin_sizes, getchar, length, comp);
  		}
  	}

  	//Holds the bin vector and makes the initial recursive call
  	template <class RandomAccessIter, class data_type, class unsignedchar_type>
  	inline void 
  	string_sort(RandomAccessIter first, RandomAccessIter last, data_type, unsignedchar_type)
  	{
  		std::vector<size_t> bin_sizes;
  		std::vector<RandomAccessIter> bin_cache;
  		string_sort_rec<RandomAccessIter, data_type, unsignedchar_type>(first, last, 0, bin_cache, 0, bin_sizes);
  	}

  	//Holds the bin vector and makes the initial recursive call
  	template <class RandomAccessIter, class data_type, class unsignedchar_type>
  	inline void 
  	reverse_string_sort(RandomAccessIter first, RandomAccessIter last, data_type, unsignedchar_type)
  	{
  		std::vector<size_t> bin_sizes;
  		std::vector<RandomAccessIter> bin_cache;
  		reverse_string_sort_rec<RandomAccessIter, data_type, unsignedchar_type>(first, last, 0, bin_cache, 0, bin_sizes);
  	}

  	//Holds the bin vector and makes the initial recursive call
  	template <class RandomAccessIter, class get_char, class get_length, class data_type, class unsignedchar_type>
  	inline void 
  	string_sort(RandomAccessIter first, RandomAccessIter last, get_char getchar, get_length length, data_type, unsignedchar_type)
  	{
  		std::vector<size_t> bin_sizes;
  		std::vector<RandomAccessIter> bin_cache;
  		string_sort_rec<RandomAccessIter, data_type, unsignedchar_type, get_char, get_length>(first, last, 0, bin_cache, 0, bin_sizes, getchar, length);
  	}

  	//Holds the bin vector and makes the initial recursive call
  	template <class RandomAccessIter, class get_char, class get_length, class compare, class data_type, class unsignedchar_type>
  	inline void 
  	string_sort(RandomAccessIter first, RandomAccessIter last, get_char getchar, get_length length, compare comp, data_type, unsignedchar_type)
  	{
  		std::vector<size_t> bin_sizes;
  		std::vector<RandomAccessIter> bin_cache;
  		string_sort_rec<RandomAccessIter, data_type, unsignedchar_type, get_char, get_length, compare>(first, last, 0, bin_cache, 0, bin_sizes, getchar, length, comp);
  	}

  	//Holds the bin vector and makes the initial recursive call
  	template <class RandomAccessIter, class get_char, class get_length, class compare, class data_type, class unsignedchar_type>
  	inline void 
  	reverse_string_sort(RandomAccessIter first, RandomAccessIter last, get_char getchar, get_length length, compare comp, data_type, unsignedchar_type)
  	{
  		std::vector<size_t> bin_sizes;
  		std::vector<RandomAccessIter> bin_cache;
  		reverse_string_sort_rec<RandomAccessIter, data_type, unsignedchar_type, get_char, get_length, compare>(first, last, 0, bin_cache, 0, bin_sizes, getchar, length, comp);
  	}
  }

  //Allows character-type overloads
  template <class RandomAccessIter, class unsignedchar_type>
  inline void string_sort(RandomAccessIter first, RandomAccessIter last, unsignedchar_type unused) 
  {
  	//Don't sort if it's too small to optimize
  	if(last - first < detail::MIN_SORT_SIZE)
  		std::sort(first, last);
  	else
  		detail::string_sort(first, last, *first, unused);
  }

  //Top-level sorting call; wraps using default of unsigned char
  template <class RandomAccessIter>
  inline void string_sort(RandomAccessIter first, RandomAccessIter last) 
  {
  	unsigned char unused = '\0';
  	string_sort(first, last, unused);
  }

  //Allows character-type overloads
  template <class RandomAccessIter, class compare, class unsignedchar_type>
  inline void reverse_string_sort(RandomAccessIter first, RandomAccessIter last, compare comp, unsignedchar_type unused) 
  {
  	//Don't sort if it's too small to optimize
  	if(last - first < detail::MIN_SORT_SIZE)
  		std::sort(first, last, comp);
  	else
  		detail::reverse_string_sort(first, last, *first, unused);
  }

  //Top-level sorting call; wraps using default of unsigned char
  template <class RandomAccessIter, class compare>
  inline void reverse_string_sort(RandomAccessIter first, RandomAccessIter last, compare comp) 
  {
  	unsigned char unused = '\0';
  	reverse_string_sort(first, last, comp, unused);
  }

  template <class RandomAccessIter, class get_char, class get_length>
  inline void string_sort(RandomAccessIter first, RandomAccessIter last, get_char getchar, get_length length) 
  {
  	//Don't sort if it's too small to optimize
  	if(last - first < detail::MIN_SORT_SIZE)
  		std::sort(first, last);
  	else {
  		//skipping past empties at the beginning, which allows us to get the character type 
  		//.empty() is not used so as not to require a user declaration of it
  		while(!length(*first)) {
  			if(++first == last)
  				return;
  		}
  		detail::string_sort(first, last, getchar, length, *first, getchar((*first), 0));
  	}
  }

  template <class RandomAccessIter, class get_char, class get_length, class compare>
  inline void string_sort(RandomAccessIter first, RandomAccessIter last, get_char getchar, get_length length, compare comp) 
  {
  	//Don't sort if it's too small to optimize
  	if(last - first < detail::MIN_SORT_SIZE)
  		std::sort(first, last, comp);
  	else {
  		//skipping past empties at the beginning, which allows us to get the character type 
  		//.empty() is not used so as not to require a user declaration of it
  		while(!length(*first)) {
  			if(++first == last)
  				return;
  		}
  		detail::string_sort(first, last, getchar, length, comp, *first, getchar((*first), 0));
  	}
  }

  template <class RandomAccessIter, class get_char, class get_length, class compare>
  inline void reverse_string_sort(RandomAccessIter first, RandomAccessIter last, get_char getchar, get_length length, compare comp) 
  {
  	//Don't sort if it's too small to optimize
  	if(last - first < detail::MIN_SORT_SIZE)
  		std::sort(first, last, comp);
  	else {
  		//skipping past empties at the beginning, which allows us to get the character type 
  		//.empty() is not used so as not to require a user declaration of it
  		while(!length(*(--last))) {
  			//Note: if there is just one non-empty, and it's at the beginning, then it's already in sorted order
  			if(first == last)
  				return;
  		}
  		//making last just after the end of the non-empty part of the array
  		++last;
  		detail::reverse_string_sort(first, last, getchar, length, comp, *first, getchar((*first), 0));
  	}
  }
}

#endif
