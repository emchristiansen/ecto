#ifndef ROS_TOPIC_HPP_INCLUDED
#define ROS_TOPIC_HPP_INCLUDED

#include <iostream>
#include <ros/message.hpp>
#include <ros/util.hpp>
#include <ros/allocator.hpp>

#include <boost/interprocess/shared_memory_object.hpp>
#include <boost/interprocess/mapped_region.hpp>

namespace bip = boost::interprocess;

template <typename T>
class queue 
{
  std::string name;
  unsigned size;
  T* data;
  bip::shared_memory_object shm;
  bip::mapped_region reg;

public:

  queue(const std::string& name_, std::size_t size_, bip::mode_t mode)
    : name(name_)
    , shm(bip::open_or_create, name.c_str(), mode)
  {

  }

  message<T> create() 
  {
    SHOW("making message");
    return message<T>();
  }
};

#endif
